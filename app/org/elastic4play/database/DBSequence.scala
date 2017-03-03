package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import com.sksamuel.elastic4s.ElasticDsl.update
import com.sksamuel.elastic4s.IndexAndTypes.apply

import org.elastic4play.models.{ AttributeFormat ⇒ F, AttributeOption ⇒ O, ModelAttributes }

class SequenceModel extends ModelAttributes("sequence") {
  val counter = attribute("sequence", F.numberFmt, "Value of the sequence", O.model)
}

@Singleton
class DBSequence @Inject() (
    db: DBConfiguration,
    implicit val ec: ExecutionContext) {

  def apply(seqId: String): Future[Int] = {
    db.execute {
      update(seqId)
        .in(db.indexName → "sequence")
        .upsert("counter" → 1)
        .script("ctx._source.counter += 1")
        .retryOnConflict(5)
        .fetchSource(Seq("counter"), Nil)
    } map { updateResponse ⇒
      updateResponse.get.field("counter").getValue().asInstanceOf[java.lang.Number].intValue()
    }
  }
}