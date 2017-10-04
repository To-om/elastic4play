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

  def apply(entity: Entity, nparent: Int, withStats: Boolean): Future[JsObject] = {

    val entityWithParent = entity._parent match {
      case None ⇒ Future.successful(entity.toJson)
      case Some(parentId) if nparent > 0 ⇒

        val parentModel = entity._model.parents.head
        findSrv(Some(parentModel.name), withId(parentId), Some("0-1"), Nil)
          .mapAsync(1) { parent ⇒
            apply(parent, nparent - 1, withStats).map { parent ⇒
              entity.toJson + (parentModel.name → parent)
            }
          }
          .runWith(Sink.headOption)
          .map(_.getOrElse {
            logger.warn(s"Child entity ($entity) has no parent !")
            JsObject(Nil)
          })
    }

    //    if (withStats) {
    //      for {
    //        e ← entityWithParent
    //        model = entity._model
    //        s ← model.getStats(entity.asInstanceOf[model.E])
    //      } yield e + ("stats" → s)
    //    }
    //    else entityWithParent
    entityWithParent
  }

  def apply[A, E <: Entity](entities: Source[E, A], nparent: Int, withStats: Boolean): Source[JsObject, A] = {
    entities.mapAsync(5) { entity ⇒ apply(entity, nparent, withStats) }
  }
}