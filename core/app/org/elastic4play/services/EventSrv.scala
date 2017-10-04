package org.elastic4play.services

import java.util.Date
import javax.inject.Singleton

import akka.actor.{ ActorRef, actorRef2Scala }
import akka.event.{ ActorEventBus, SubchannelClassification }
import akka.util.Subclassification

import org.elastic4play.models.{ Entity, FPath, HiveEnumeration, UpdateOps }
import play.api.Logger
import play.api.libs.json.{ JsArray, JsObject }
import play.api.mvc.{ RequestHeader, Result }
import scala.util.Try

trait EventMessage

object AuditableAction extends Enumeration with HiveEnumeration {
  type Type = Value
  val Update, Creation, Delete, Get = Value
}

case class RequestProcessStart(request: RequestHeader) extends EventMessage
case class RequestProcessEnd(request: RequestHeader, result: Try[Result]) extends EventMessage

case class AuditOperation(
  entity: Entity,
  action: AuditableAction.Type,
  details: JsObject,
  authContext: AuthContext,
  date: Date = new Date()) extends EventMessage

object AuditOperation {
  def create(entity: Entity, details: JsObject)(implicit authContext: AuthContext): AuditOperation =
    AuditOperation(
      entity,
      AuditableAction.Creation,
      details,
      authContext,
      new Date)

  def update(entity: Entity, updates: Map[FPath, UpdateOps.DBType])(implicit authContext: AuthContext): AuditOperation = {
    val details = JsObject(updates.flatMap {
      case (name, UpdateOps.SetDBAttribute(Some(value))) ⇒ Seq(name.toString → value)
      case (name, UpdateOps.UnsetAttribute)              ⇒ Seq(name.toString → JsArray())
      case (_, UpdateOps.SetDBAttribute(None))           ⇒ Nil
    })
    AuditOperation(
      entity,
      AuditableAction.Update,
      details,
      authContext,
      new Date)
  }
  def delete(entity: Entity)(implicit authContext: AuthContext): AuditOperation =
    AuditOperation(entity, AuditableAction.Delete, JsObject(Nil), authContext)
}

@Singleton
class EventSrv extends ActorEventBus with SubchannelClassification {
  lazy val log = Logger(getClass)
  override type Classifier = Class[_ <: EventMessage]
  override type Event = EventMessage

  override protected def classify(event: EventMessage): Classifier = event.getClass
  override protected def publish(event: EventMessage, subscriber: ActorRef): Unit = subscriber ! event

  implicit protected def subclassification: Subclassification[Classifier] = new Subclassification[Classifier] {
    def isEqual(x: Classifier, y: Classifier): Boolean = x == y
    def isSubclass(x: Classifier, y: Classifier): Boolean = y.isAssignableFrom(x)
  }
}

