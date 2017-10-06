package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import com.sksamuel.elastic4s.ElasticDsl.{ idsQuery, search }
import org.elastic4play.NotFoundError
import org.elastic4play.database.JsonFormat.richSearchHitWrites
import play.api.libs.json.JsObject

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class DBGet @Inject() (
    db: DBConfiguration,
    implicit val ec: ExecutionContext) {

  /**
   * Retrieve entities from ElasticSearch
   * @param modelName the name of the model (ie. document type)
   * @param id identifier of the entity to retrieve
   * @return the entity
   */
  def apply(modelName: String, id: String): Future[JsObject] = {
    db
      .execute {
        // Search by id is not possible on child entity without routing information => id query
        search(db.indexName)
          .query(idsQuery(id).types(modelName))
      }
      .map { searchResponse ⇒
        searchResponse
          .hits
          .headOption
          .map(richSearchHitWrites.writes)
          .getOrElse(throw NotFoundError(s"$modelName $id not found"))
      }
  }
}