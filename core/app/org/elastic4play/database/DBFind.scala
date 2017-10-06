package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import akka.stream.scaladsl.Source
import akka.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, OutHandler }
import akka.stream.{ Attributes, Materializer, Outlet, SourceShape }
import com.sksamuel.elastic4s.ElasticDsl.searchScroll
import com.sksamuel.elastic4s.searches.{ RichSearchHit, RichSearchResponse, SearchDefinition }
import org.elastic4play.SearchError
import play.api.libs.json._
import play.api.{ Configuration, Logger }

import scala.concurrent.duration.{ DurationLong, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

/**
 * Service class responsible for entity search
 */
@Singleton
class DBFind(
    pageSize: Int,
    keepAlive: FiniteDuration,
    db: DBConfiguration,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  @Inject def this(
    configuration: Configuration,
    db: DBConfiguration,
    ec: ExecutionContext,
    mat: Materializer) =
    this(
      configuration.get[Int]("search.pagesize"),
      configuration.getMillis("search.keepalive").millis,
      db,
      ec,
      mat)

  private[database] val indexName = db.indexName
  private[database] val keepAliveStr = keepAlive.toMillis + "ms"
  private[DBFind] lazy val logger = Logger(getClass)

  /**
   * return a new instance of DBFind but using another DBConfiguration
   */
  def switchTo(otherDB: DBConfiguration) = new DBFind(pageSize, keepAlive, otherDB, ec, mat)

  /**
   * Extract offset and limit from optional range
   * Range has the following format : "start-end"
   * If format is invalid of range is None, this function returns (0, 10)
   */
  private[database] def getOffsetAndLimitFromRange(range: Option[String]): (Int, Int) = {
    range match {
      case None        ⇒ (0, 10)
      case Some("all") ⇒ (0, Int.MaxValue)
      case Some(r) ⇒
        val Array(_offset, _end, _*) = (r + "-0").split("-", 3)
        val offset = Try(Math.max(0, _offset.toInt)).getOrElse(0)
        val end = Try(_end.toInt).getOrElse(offset + 10)
        if (end <= offset)
          (offset, 10)
        else
          (offset, end - offset)
    }
  }

  /**
   * Execute the search definition using scroll
   */
  private[database] def searchWithScroll(searchDefinition: SearchDefinition, limit: Int): Source[RichSearchHit, Future[Long]] = {
    import akka.stream.scaladsl._

    val sourceGraph = new SearchSource(
      db,
      searchDefinition limit pageSize,
      keepAliveStr,
      limit)
    Source.fromGraph(sourceGraph)
  }

  /**
   * Execute the search definition
   */
  private[database] def searchWithoutScroll(searchDefinition: SearchDefinition, limit: Int): Source[RichSearchHit, Future[Long]] = {
    val resp = db.execute(searchDefinition limit limit)
    Source
      .fromFuture(resp)
      .mapMaterializedValue(_ ⇒ resp.map(_.totalHits))
      .mapConcat { resp ⇒ resp.hits.toList }
  }

  /**
   * Transform search hit into JsObject
   * This function parses hit source add _type, _routing, _parent and _id attributes
   */
  // TODO duplicated with org.elastic4play.database.JsonFormat.richSearchHitWrites
  private[database] def hit2json(hit: RichSearchHit) = {
    val id = JsString(hit.id)
    Json.parse(hit.sourceAsString).as[JsObject] +
      ("_type" → JsString(hit.`type`)) +
      ("_routing" → hit.fields.get("_routing").fold(id)(r ⇒ JsString(r.java.getValue[String]))) +
      ("_parent" → hit.fields.get("_parent").fold[JsValue](JsNull)(r ⇒ JsString(r.java.getValue[String]))) +
      ("_id" → id)
  }

  /**
   * Search entities in ElasticSearch
   *
   * @param range  first and last entities to retrieve, for example "23-42" (default value is "0-10")
   * @param sortBy define order of the entities by specifying field names used in sort. Fields can be prefixed by
   *               "-" for descendant or "+" for ascendant sort (ascendant by default).
   * @param query  a function that build a SearchDefinition using the index name
   * @return Source (akka stream) of JsObject. The source is materialized as future of long that contains the total number of entities.
   */
  def apply(range: Option[String], sortBy: Seq[String])(query: (String) ⇒ SearchDefinition): Source[JsObject, Future[Long]] = {
    val (offset, limit) = getOffsetAndLimitFromRange(range)
    val sortDef = DBUtils.sortDefinition(sortBy)
    val searchDefinition = query(indexName).storedFields("_source", "_routing", "_parent").sortBy(sortDef)

    // FIXME log.debug(s"search ${searchDefinition._builder}")
    val src = if (limit > pageSize) {
      searchWithScroll(searchDefinition, limit).drop(offset.toLong)
    }
    else {
      searchWithoutScroll(searchDefinition.start(offset), limit)
    }

    src.map(hit2json)
  }

  /**
   * Execute the search definition
   * This function is used to run aggregations
   */
  def apply(query: (String) ⇒ SearchDefinition): Future[RichSearchResponse] = {
    val searchDefinition = query(indexName)
    logger.info(s"Search $searchDefinition")
    db.execute(searchDefinition)
      .recoverWith {
        case t if DBUtils.isIndexMissing(t) ⇒ Future.failed(t)
        case t                              ⇒ Future.failed(SearchError("Invalid search query", t))
      }
  }
}

class SearchSource(
    db: DBConfiguration,
    searchDefinition: SearchDefinition,
    keepAliveStr: String,
    max: Int)(implicit ec: ExecutionContext) extends GraphStageWithMaterializedValue[SourceShape[RichSearchHit], Future[Long]] {
  val out: Outlet[RichSearchHit] = Outlet("SearchSource")

  override val shape: SourceShape[RichSearchHit] = SourceShape(out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Long]) = {
    val promise = Promise[Long]
    val logic = new GraphStageLogic(shape) {
      private var processed: Int = 0
      private var scrollId: Option[String] = None

      override def preStart(): Unit = {
        val callback = getAsyncCallback[Try[RichSearchResponse]] {
          case Success(rsr) ⇒
            promise.success(rsr.totalHits)
            scrollId = rsr.scrollIdOpt
            if (rsr.hits.length > max) {
              emitMultiple(out, rsr.hits.take(max).toIterator)
              completeStage()
            }
            else {
              emitMultiple(out, rsr.hits.toIterator)
              processed = rsr.hits.length
            }

          case Failure(t) ⇒
            promise.failure(t)
            failStage(t)
        }

        db.execute(searchDefinition.scroll(keepAliveStr)).onComplete(callback.invoke)
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          scrollId match {
            case Some(sid) ⇒
              val callback = getAsyncCallback[Try[RichSearchResponse]] {
                case Success(rsr) if rsr.isTimedOut ⇒ fail(out, SearchError("Search timeout", null))
                case Success(rsr) if rsr.isEmpty    ⇒ completeStage()
                case Success(rsr) ⇒
                  if (processed + rsr.hits.length > max) {
                    emitMultiple(out, rsr.hits.take(max - processed).toIterator)
                    completeStage()
                  }
                  else {
                    emitMultiple(out, rsr.hits.toIterator)
                    processed += rsr.hits.length
                  }
                case Failure(t) ⇒ failStage(t)
              }
              db.execute(searchScroll(sid).keepAlive(keepAliveStr)).onComplete(callback.invoke)
            case None ⇒ failStage(new Exception("no scroll id ?!"))
          }
        }

      })
    }

    (logic, promise.future)
  }
}