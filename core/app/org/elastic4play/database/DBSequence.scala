package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import com.sksamuel.elastic4s.ElasticDsl.update
import com.sksamuel.elastic4s.IndexAndTypes.apply

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class DBSequence @Inject() (
  db: DBConfiguration,
  implicit val ec: ExecutionContext) {

  def next(seqId: String): Future[Int] = {
    db.execute {
      update(seqId)
        .in(db.indexName → "sequence")
        .upsert("counter" → 1)
        .script("ctx._source.counter += 1")
        .retryOnConflict(5)
        .fetchSource(Seq("counter"), Nil)
    } map { updateResponse ⇒
      updateResponse.get.field("counter").getValue.asInstanceOf[java.lang.Number].intValue()
    }
  }
}