package org.elastic4play.services

import javax.inject.{ Inject, Singleton }

import akka.stream.scaladsl.Source
//import com.sksamuel.elastic4s.ElasticDsl.{ avgAggregation, boolQuery, dateHistogramAggregation, existsQuery, filterAggregation, hasChildQuery, hasParentQuery, idsQuery, matchAllQuery, maxAggregation, minAggregation, nestedQuery, query, rangeQuery, search, sumAggregation, termQuery, termsAggregation, termsQuery, topHitsAggregation }
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.searches.aggs._
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import org.apache.lucene.search.join.ScoreMode
import org.elastic4play.BadRequestError
import org.elastic4play.database.JsonFormat.searchHitWrites
import org.elastic4play.database.{ DBFind, DBUtils }
import org.elastic4play.models.{ Entity, Model }
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.search.aggregations.bucket.filter.Filter
import org.elasticsearch.search.aggregations.bucket.filters.Filters
import org.elasticsearch.search.aggregations.bucket.histogram.{ DateHistogramInterval, Histogram }
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Order
import org.elasticsearch.search.aggregations.metrics.avg.Avg
import org.elasticsearch.search.aggregations.metrics.max.Max
import org.elasticsearch.search.aggregations.metrics.min.Min
import org.elasticsearch.search.aggregations.metrics.sum.Sum
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits
import org.joda.time.DateTime
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.{ JsArray, JsNumber, JsObject }

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
//import scala.language.reflectiveCalls
import scala.math.BigDecimal.{ double2bigDecimal, int2bigDecimal, long2bigDecimal }
import scala.util.Try

case class QueryDef(query: QueryDefinition)

trait Agg {
  def apply(model: Model): Seq[AggregationDefinition]

  def processResult(model: Model, aggregations: RichAggregations): JsObject
}

trait FieldSelectable {
  self: Agg ⇒
  val fieldName: String

  def script(s: String): AggregationDefinition

  def field(f: String): AggregationDefinition

  def apply(model: Model): Seq[AggregationDefinition] = {
    fieldName.split("\\.", 3) match {
      case Array("computed", c) ⇒
        val s = model.computedMetrics.getOrElse(
          c,
          throw BadRequestError(s"Field $fieldName is unknown in ${model.name}"))
        Seq(script(s))
      case array ⇒
        //        val attribute = model.attributes.find(_.name == array(0)).getOrElse {
        //          throw BadRequestError(s"Field $fieldName is unknown in ${model.name}")
        //        }
        // TODO check attribute type
        Seq(field(fieldName))
    }
  }
}

class SelectAvg(val fieldName: String) extends Agg with FieldSelectable {
  val name = s"avg_$fieldName"

  def script(s: String): AggregationDefinition = avgAggregation(name).script(s)

  def field(f: String): AggregationDefinition = avgAggregation(name).field(f)

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val avg = aggregations.getAs[Avg](name)
    val value = Try(JsNumber(avg.getValue)).toOption.getOrElse(JsNumber(0))
    JsObject(Seq(avg.getName → value))
  }
}

class SelectMin(val fieldName: String) extends Agg with FieldSelectable {
  val name = s"min_$fieldName"

  def script(s: String): AggregationDefinition = minAggregation(name).script(s)

  def field(f: String): AggregationDefinition = minAggregation(name).field(f)

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val min = aggregations.getAs[Min](name)
    val value = Try(JsNumber(min.getValue)).toOption.getOrElse(JsNumber(0))
    JsObject(Seq(min.getName → value))
  }
}

class SelectMax(val fieldName: String) extends Agg with FieldSelectable {
  val name = s"max_$fieldName"

  def script(s: String): AggregationDefinition = maxAggregation(name).script(s)

  def field(f: String): AggregationDefinition = maxAggregation(name).field(f)

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val max = aggregations.getAs[Max](name)
    val value = Try(JsNumber(max.getValue)).toOption.getOrElse(JsNumber(0))
    JsObject(Seq(max.getName → value))
  }
}

class SelectSum(val fieldName: String) extends Agg with FieldSelectable {
  val name = s"sum_$fieldName"

  def script(s: String): AggregationDefinition = sumAggregation(name).script(s)

  def field(f: String): AggregationDefinition = sumAggregation(name).field(f)

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val sum = aggregations.getAs[Sum](name)
    val value = Try(JsNumber(sum.getValue)).toOption.getOrElse(JsNumber(0))
    JsObject(Seq(sum.getName → value))
  }
}

object SelectCount extends Agg {
  val name = "count"

  override def apply(model: Model) = Seq(filterAggregation(name).query(matchAllQuery))

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val count = aggregations.getAs[Filter](name)
    JsObject(Seq(count.getName → JsNumber(count.getDocCount)))
  }
}

class SelectTop(size: Int, sortBy: Seq[String]) extends Agg {
  val name = "top"

  def apply(model: Model): Seq[AggregationDefinition] = Seq(topHitsAggregation(name).size(size).sortBy(DBUtils.sortDefinition(sortBy)))

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val top = aggregations.getAs[TopHits](name)
    // "top" -> JsArray(top.getHits.getHits.map(h => FindSrv.hit2json(RichSearchHit(h))))
    //JsObject(Seq("top" → JsArray(top.getHits.getHits.map(h ⇒ DBUtils.hit2json(None, new RichSearchHit(h))))))
    JsObject(Seq("top" → JsArray(top.getHits.getHits.map(searchHitWrites.writes))))
  }
}

class GroupByCategory(categories: Map[String, QueryDef], subAggs: Seq[Agg]) extends Agg {
  val name = "categories"

  def apply(model: Model): Seq[AggregationDefinition] = {
    val filters = categories.map {
      case (_name, queryDef) ⇒ _name → queryDef.query
    }
    val subAggregations = subAggs.flatMap(_.apply(model))
    Seq(KeyedFiltersAggregationDefinition(name, filters).subAggregations(subAggregations))
  }

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val filters = aggregations.getAs[Filters](name)
    JsObject {
      categories.keys.toSeq.map { cat ⇒
        val subAggResults = filters.getBucketByKey(cat).getAggregations
        cat → subAggs.map(_.processResult(model, RichAggregations(subAggResults)))
          .reduceOption(_ ++ _)
          .getOrElse(JsObject(Nil))
      }
    }
  }

}

class GroupByTime(fields: Seq[String], interval: String, subAggs: Seq[Agg]) extends Agg {
  def apply(model: Model): Seq[AggregationDefinition] = {
    fields.map { f ⇒
      dateHistogramAggregation(s"datehistogram_$f").field(f).interval(new DateHistogramInterval(interval)).subAggregations(subAggs.flatMap(_.apply(model)))
    }
  }

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val aggs = fields.map { f ⇒
      val buckets = aggregations.getAs[Histogram](s"datehistogram_$f").getBuckets
      f → buckets.asScala.map { bucket ⇒
        val results = subAggs
          .map(_.processResult(model, RichAggregations(bucket.getAggregations)))
          .reduceOption(_ ++ _)
          .getOrElse(JsObject(Nil))
        // date -> obj(key{avg, min} -> value)
        (bucket.getKey.asInstanceOf[DateTime].getMillis.toString → results)
      }.toMap
    }.toMap
    val keys = aggs.values.flatMap(_.keys).toSet
    JsObject {
      keys.map { date ⇒
        date → JsObject(aggs.map {
          case (df, values) ⇒
            df → values.getOrElse(date, JsObject(Nil))
        })
      }.toMap
    }
  }
}

class GroupByField(field: String, size: Option[Int], sortBy: Seq[String], subAggs: Seq[Agg]) extends Agg {
  def apply(model: Model): Seq[AggregationDefinition] = {
    Seq(termsAggregation(s"term_$field").field(field).subAggregations(subAggs.flatMap(_.apply(model))))
      .map { agg ⇒ size.fold(agg)(s ⇒ agg.size(s)) }
      .map {
        case agg if sortBy.isEmpty ⇒ agg
        case agg ⇒
          agg.order(Order.compound(sortBy
            .flatMap {
              case f if f.startsWith("+") ⇒ Some(Order.aggregation(f.drop(1), true))
              case f if f.startsWith("-") ⇒ Some(Order.aggregation(f.drop(1), false))
              case f if f.length() > 0    ⇒ Some(Order.aggregation(f, true))
            }
            .asJava))
      }
  }

  def processResult(model: Model, aggregations: RichAggregations): JsObject = {
    val buckets = aggregations.getAs[Terms](s"term_$field").getBuckets
    JsObject {
      buckets.asScala.map { bucket ⇒
        val results = subAggs
          .map(_.processResult(model, RichAggregations(bucket.getAggregations)))
          .reduceOption(_ ++ _)
          .getOrElse(JsObject(Nil))
        bucket.getKeyAsString → results
      }.toMap
    }
  }
}

object QueryDSL {
  def selectAvg(field: String) = new SelectAvg(field)

  def selectMin(field: String) = new SelectMin(field)

  def selectMax(field: String) = new SelectMax(field)

  def selectSum(field: String) = new SelectSum(field)

  def selectCount = SelectCount

  def selectTop(size: Int, sortBy: Seq[String]) = new SelectTop(size, sortBy)

  def groupByTime(fields: Seq[String], interval: String, selectables: Agg*) = new GroupByTime(fields, interval, selectables)

  def groupByField(field: String, selectables: Agg*) = new GroupByField(field, None, Nil, selectables)

  def groupByField(field: String, size: Int, sortBy: Seq[String], selectables: Agg*) = new GroupByField(field, Some(size), sortBy, selectables)

  def groupByCaterogy(categories: Map[String, QueryDef], selectables: Agg*) = new GroupByCategory(categories, selectables)

  private def nestedField(field: String, q: (String) ⇒ QueryDefinition) = {
    val names = field.split("\\.")
    names.init.foldRight(q(field)) {
      case (subName, queryDef) ⇒ nestedQuery(subName).query(queryDef).scoreMode(ScoreMode.None)
    }
  }

  implicit class SearchField(field: String) {
    def ~=(value: Any) = QueryDef(nestedField(field, termQuery(_, value)))

    def ~!=(value: Any): QueryDef = not(QueryDef(nestedField(field, termQuery(_, value))))

    def ~<(value: Any) = QueryDef(nestedField(field, rangeQuery(_).lt(value.toString)))

    def ~>(value: Any) = QueryDef(nestedField(field, rangeQuery(_).gt(value.toString)))

    def ~<=(value: Any) = QueryDef(nestedField(field, rangeQuery(_).lte(value.toString)))

    def ~>=(value: Any) = QueryDef(nestedField(field, rangeQuery(_).gte(value.toString)))

    def ~<>(value: (Any, Any)) = QueryDef(nestedField(field, rangeQuery(_).gt(value._1.toString).lt(value._2.toString)))

    def ~=<>=(value: (Any, Any)) = QueryDef(nestedField(field, rangeQuery(_).gt(value._1.toString).lt(value._2.toString)))

    def in(values: AnyRef*) = QueryDef(nestedField(field, termsQuery(_, values)))
  }

  def ofType(value: String) = QueryDef(termQuery("_type", value))

  def withId(entityIds: String*): QueryDef = QueryDef(idsQuery(entityIds))

  def any: QueryDef = QueryDef(matchAllQuery)

  def contains(field: String): QueryDef = QueryDef(nestedField(field, existsQuery))

  def or(queries: QueryDef*): QueryDef = or(queries)

  def or(queries: Iterable[QueryDef]): QueryDef = QueryDef(boolQuery().should(queries.map(_.query)))

  def and(queries: QueryDef*): QueryDef = QueryDef(boolQuery().must(queries.map(_.query)))

  def and(queries: Iterable[QueryDef]): QueryDef = QueryDef(boolQuery().must(queries.map(_.query)))

  def not(query: QueryDef): QueryDef = QueryDef(boolQuery.not(query.query))

  def child(childType: String, query: QueryDef): QueryDef = QueryDef(hasChildQuery(childType).query(query.query).scoreMode(ScoreMode.None))

  def parent(parentType: String, query: QueryDef): QueryDef = QueryDef(hasParentQuery(parentType).query(query.query).scoreMode(false))

  def string(queryString: String): QueryDef = QueryDef(query(queryString))
}

@Singleton
class FindSrv @Inject() (
  dbFind: DBFind,
  modelSrv: ModelSrv,
  implicit val ec: ExecutionContext) {

  //def switchTo(db: DBConfiguration) = new FindSrv(db.switchTo(db), modelSrv, ec)

  def apply(modelName: Option[String], queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): Source[Entity, Future[Long]] = {
    dbFind(range, sortBy)(indexName ⇒ modelName.fold(search(indexName))(m ⇒ search(indexName, m)).query(queryDef.query))
      .map { attrs ⇒
        modelName match {
          //case Some("audit") => auditModel.get()(attrs)
          case Some(m) ⇒ modelSrv(m).getOrElse(sys.error("TODO")).databaseReads(Some(attrs)).getOrElse(sys.error("TODO"))
          case None ⇒
            val tpe = (attrs \ "_type").asOpt[String].getOrElse(sys.error("TODO"))
            val model = modelSrv(tpe).getOrElse(sys.error("TODO"))
            model.databaseReads(Some(attrs)).getOrElse(sys.error("TODO"))
        }
      }
  }

  //  def apply(model: Model, queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): (Source[BaseEntity, NotUsed], Future[Long]) = {
  //    val (src, total) = dbfind(range, sortBy)(indexName ⇒ search(indexName, model.name).query(queryDef.query))
  //    val entities = src.map(attrs ⇒ model(attrs))
  //    (entities, total)
  //  }

  def apply[E <: Entity](model: Model.Base[E], queryDef: QueryDef, range: Option[String], sortBy: Seq[String]): Source[E, Future[Long]] = {
    dbFind(range, sortBy)(indexName ⇒ search(indexName, model.name).query(queryDef.query))
      .map(attrs ⇒ model.databaseReads(Some(attrs)).getOrElse(sys.error("TODO")))
  }

  def apply(model: Model, queryDef: QueryDef, aggs: Agg*): Future[JsObject] = {
    dbFind(indexName ⇒ search(indexName, model.name).query(queryDef.query).aggregations(aggs.flatMap(_.apply(model))).searchType(SearchType.QUERY_THEN_FETCH).size(0))
      .map { searchResponse ⇒
        aggs
          .map(_.processResult(model, searchResponse.aggregations))
          .reduceOption(_ ++ _)
          .getOrElse(JsObject(Nil))
      }
  }
}