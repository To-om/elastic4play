package org.elastic4play.services

import javax.inject.{ Inject, Singleton }

import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.sksamuel.elastic4s.ElasticDsl.search
import org.elastic4play.database.{ DBCreate, DBFind, DBRemove }
import org.elastic4play.utils.{ Hasher, RichFuture }
import play.api.cache.SyncCacheApi
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._

import scala.collection.immutable.Seq
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ ExecutionContext, Future }

case class DBListItem(dblist: String, value: String) {
  def _id = Hasher("MD5").fromString(value).head.toString()
  def mapTo[A](implicit reads: Reads[A]): A = reads.reads(Json.parse(value)).get
}

trait DBList {
  def cachedItems: Seq[DBListItem]
  def getItems(): Source[DBListItem, Future[Long]]
  def getItems[A](implicit reads: Reads[A]): Source[(String, A), Future[Long]]
  def addItem[A](item: A)(implicit writes: Writes[A]): Future[DBListItem]
}

@Singleton
class DBLists @Inject() (
    dbFind: DBFind,
    dbCreate: DBCreate,
    dbRemove: DBRemove,
    cache: SyncCacheApi,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  /**
   * Returns list of all dblist name
   */
  def listAll: Future[collection.Set[String]] = {
    dbFind(Some("all"), Nil)(indexName ⇒ search(indexName).query(QueryDSL.any.query))
      .mapConcat(json ⇒ (json \ "dblist").asOpt[String].toList)
      .runWith(Sink.seq)
      .map(_.toSet)
  }

  def deleteItem(itemId: String): Future[Boolean] = dbRemove("dblist", itemId, itemId)

  def apply(name: String): DBList = new DBList {
    def cachedItems: Seq[DBListItem] = cache.getOrElseUpdate("dblist" + "_" + name, 10.seconds) {
      getItems().runWith(Sink.seq).await
    }

    def getItems(): Source[DBListItem, Future[Long]] = {
      import org.elastic4play.services.QueryDSL._
      dbFind(Some("all"), Nil)(indexName ⇒ search(indexName).query(("dblist" ~= name).query))
        .mapConcat { json ⇒
          for {
            dblist ← (json \ "dblist").asOpt[String].toList
            value ← (json \ "value").asOpt[String]
          } yield DBListItem(dblist, value)
        }
    }

    def getItems[A](implicit reads: Reads[A]): Source[(String, A), Future[Long]] = {
      getItems().map(item ⇒ (item._id, item.mapTo[A]))
    }

    def addItem[A](item: A)(implicit writes: Writes[A]): Future[DBListItem] = {
      val value = Json.toJson(item)
      val dbItem = DBListItem(name, value.toString)
      dbCreate("dblist", Some(dbItem._id), None, None, Json.obj("dblist" → name, "value" → JsString(value.toString)))
        .map(_ ⇒ dbItem)
    }
  }

}