package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }

import akka.stream.scaladsl.Sink

import play.api.Logger
import play.api.libs.json.{ JsNull, JsObject, JsString, JsValue }
import play.api.libs.json.JsValue.jsValueToJsLookup

import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import org.elasticsearch.transport.RemoteTransportException

import com.sksamuel.elastic4s.ElasticDsl.indexInto
import com.sksamuel.elastic4s.indexes.IndexDefinition
import com.sksamuel.elastic4s.streams.RequestBuilder

import org.elastic4play.CreateError
import org.elastic4play.models.BaseEntity

/**
 * Service lass responsible for entity creation
 * This service doesn't check any attribute conformity (according to model)
 */
@Singleton
class DBCreate @Inject() (
    db: DBConfiguration,
    implicit val ec: ExecutionContext) {

  val logger = Logger(getClass)

  /**
   * Create an entity of type "modelName" with attributes
   *
   * @param modelName name of the model of the creating entity
   * @param attributes JSON object containing attributes of the creating entity. Attributes can contain _id, _parent and _routing.
   * @return created entity attributes with _id and _routing (and _parent if entity is a child)
   */
  def apply(modelName: String, attributes: JsObject): Future[JsObject] = {
    apply(modelName, None, attributes)
  }

  /**
   * Create an entity of type modelName with attributes and optionally a parent
   *
   * @param modelName name of the model of the creating entity
   * @param parent parent of the creating entity (if model is ChildModelDef
   * @param attributes JSON object containing attributes of the creating entity.
   * Attributes can contain _id, _parent and _routing. Routing and parent informations are extracted from parent parameter (if present)
   * @return created entity attributes with _id and _routing (and _parent if entity is a child)
   */
  def apply(modelName: String, parent: Option[BaseEntity], attributes: JsObject): Future[JsObject] = {
    val id = (attributes \ "_id").asOpt[String]
    val parentId = parent.map(_.id)
      .orElse((attributes \ "_parent").asOpt[String])
    val routing = parent.map(_.routing)
      .orElse((attributes \ "_routing").asOpt[String])
      .orElse(id)
    val params = CreateParams(modelName, id, parentId, routing, attributes)
    create(params)
  }

  private[database] def convertError(params: CreateParams, error: Throwable): Throwable = error match {
    case rte: RemoteTransportException ⇒ convertError(params, rte.getCause)
    // FIXME case daee: DocumentAlreadyExistsException ⇒ ConflictError(daee.getMessage, params.attributes)
    case other                         ⇒ CreateError(None, other.getMessage, params.attributes)
  }

  /**
   * Create an entity using CreateParams
   *
   * @param params data used for entity creation
   * @return entity attributes
   * attributes contain _id and _routing (and _parent if entity is a child)
   */
  private[database] def create(params: CreateParams): Future[JsObject] = {
    db.execute(params.indexDef.refresh(RefreshPolicy.IMMEDIATE)).transform(
      indexResponse ⇒ params.result(indexResponse.id),
      convertError(params, _))
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
   * Parameters required to create an entity
   *
   * @param modelName name of the model of the creating entity
   * @param id optional id of the entity
   * @param parentId optional parent id of the entity
   * @param routing optional routing information to store entity in the right shard
   * if parentId is set, routing must be the same as parent routing
   * @param attributes entity content
   */
  private[database] case class CreateParams(modelName: String, id: Option[String], parentId: Option[String], routing: Option[String], attributes: JsObject) {
    /**
     * index definition used to store entity
     */
    val indexDef: IndexDefinition = {
      // remove attributes that starts with "_" because we wan't permit to interfere with elasticsearch internal fields
      val docSource = JsObject(attributes.fields.filterNot(_._1.startsWith("_"))).toString
      addId(id).andThen(addParent(parentId)).andThen(addRouting(routing)) {
        indexInto(db.indexName, modelName).source(docSource)
      }
    }

    /**
     * build entity attribute from its attributes and id
     */
    def result(id: String): JsObject = {
      attributes +
        ("_type" → JsString(modelName)) +
        ("_id" → JsString(id)) +
        ("_parent" → parentId.fold[JsValue](JsNull)(JsString)) +
        ("_routing" → JsString(routing.getOrElse(id)))
    }
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