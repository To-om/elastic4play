package org.elastic4play.services

import javax.inject.{ Inject, Singleton }

import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import org.elastic4play.models.Entity
import play.api.Logger
import play.api.libs.json.JsObject

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AuxSrv @Inject() (
    findSrv: FindSrv,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  import org.elastic4play.services.QueryDSL._

  private[services] val logger = Logger(getClass)

  def getParents(entity: Entity, n: Int): Future[Seq[Entity]] = {
    if (n <= 0) Future.successful(Nil)
    else {
      entity._model.parents
        .take(n)
        .foldLeft[Future[(Option[String], List[Entity])]](Future.successful(entity._parent -> Nil)) { (idEntities, model) ⇒
          for {
            (maybeEntityId, entities) ← idEntities
            maybeParent ← maybeEntityId.fold[Future[Option[Entity]]](Future.successful(None)) { entityId ⇒
              findSrv(Some(model.name), withId(entityId), Some("0-1"), Nil).runWith(Sink.headOption)
            }
          } yield maybeParent.fold[(Option[String], List[Entity])](None -> entities) { parent ⇒
            Some(parent._id) -> (parent +: entities)
          }
        }
        .map(_._2)
    }
  }

  def apply[A, E <: Entity](entities: Source[E, A], nparent: Int, withStats: Boolean): Source[Seq[Entity], A] = {
    entities.mapAsync(5) {
      entity ⇒ getParents(entity, nparent)
    }
  }
}