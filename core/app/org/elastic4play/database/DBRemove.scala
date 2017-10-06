package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import com.sksamuel.elastic4s.ElasticDsl.{ RichString, delete }
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.rest.RestStatus

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class DBRemove @Inject() (
    db: DBConfiguration,
    implicit val ec: ExecutionContext) {

  def apply(modelName: String, entityId: String, entityRouting: String): Future[Boolean] = {
    db.execute {
      delete(entityId)
        .from(db.indexName / modelName)
        .routing(entityRouting)
        .refresh(RefreshPolicy.IMMEDIATE)
    }
      .map { deleteResponse ⇒
        deleteResponse.status != RestStatus.NOT_FOUND
      }
  }
}