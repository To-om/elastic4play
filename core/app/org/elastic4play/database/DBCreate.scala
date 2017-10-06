package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.indexes.IndexDefinition
import com.sksamuel.elastic4s.streams.RequestBuilder
import org.elastic4play.CreateError
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.transport.RemoteTransportException
import play.api.Logger
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json.{ JsNull, JsObject, JsString, JsValue }

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Service lass responsible for entity creation
 * This service doesn't check any attribute conformity (according to model)
 */
@Singleton
class DBCreate @Inject() (
    db: DBConfiguration,
    implicit val ec: ExecutionContext) {

  lazy val logger = Logger(getClass)

  def apply(modelName: String, id: Option[String], parent: Option[String], routing: Option[String], attributes: JsObject): Future[JsObject] = {
    val docSource = JsObject(attributes.fields.filterNot(_._1.startsWith("_"))).toString
    val indexDefinition = addId(id).andThen(addParent(parent)).andThen(addRouting(routing orElse parent)) {
      indexInto(db.indexName, modelName).source(docSource)
    }
    db.execute(indexDefinition.refresh(RefreshPolicy.IMMEDIATE)).transform(
      indexResponse ⇒
        attributes +
          ("_type" → JsString(modelName)) +
          ("_id" → JsString(indexResponse.id)) +
          ("_parent" → parent.fold[JsValue](JsNull)(JsString)) +
          ("_routing" → JsString(routing.getOrElse(indexResponse.id))), {
        case t: RemoteTransportException ⇒ CreateError(None, t.getCause.getMessage, attributes)
        case t                           ⇒ CreateError(None, t.getMessage, attributes)
      })
  }

  /**
   * add id information in index definition
   */
  private def addId(id: Option[String]): IndexDefinition ⇒ IndexDefinition = id match {
    case Some(i) ⇒ _ id i
    case None    ⇒ identity
  }

  /**
   * add parent information in index definition
   */
  private def addParent(parent: Option[String]): IndexDefinition ⇒ IndexDefinition = parent match {
    case Some(p) ⇒ _ parent p
    case None    ⇒ identity
  }

  /**
   * add routing information in index definition
   */
  private def addRouting(routing: Option[String]): IndexDefinition ⇒ IndexDefinition = routing match {
    case Some(r) ⇒ _ routing r
    case None    ⇒ identity
  }

  /**
   * Class used to build index definition based on model name and attributes
   * This class is used by sink (ElasticSearch reactive stream)
   */
  private class AttributeRequestBuilder(modelName: String) extends RequestBuilder[JsObject] {
    override def request(attributes: JsObject): IndexDefinition = {
      val docSource = JsObject(attributes.fields.filterNot(_._1.startsWith("_"))).toString
      val id = (attributes \ "_id").asOpt[String]
      val parent = (attributes \ "_parent").asOpt[String]
      val routing = (attributes \ "_routing").asOpt[String] orElse parent orElse id
      addId(id).andThen(addParent(parent)).andThen(addRouting(routing)) {
        indexInto(db.indexName, modelName).source(docSource)
      }
    }
  }

  /**
   * build a akka stream sink that create entities
   */
  def sink(modelName: String): Sink[JsObject, Future[Unit]] = db.sink(new AttributeRequestBuilder(modelName))
}