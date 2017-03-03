package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

import play.api.Logger
import play.api.libs.json.{ JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json }

import org.elasticsearch.action.support.WriteRequest.RefreshPolicy

import com.sksamuel.elastic4s.ElasticDsl.{ script, update }
import com.sksamuel.elastic4s.IndexAndTypes.apply

import org.elastic4play.models.BaseEntity

@Singleton
class DBModify @Inject() (
    db: DBConfiguration,
    implicit val ec: ExecutionContext) {
  val log = Logger(getClass)

  /**
   * Convert JSON value to java native value
   */
  private[database] def jsonToAny(json: JsValue): Any = {
    import scala.collection.JavaConversions._
    json match {
      case v: JsObject  ⇒ mapAsJavaMap(v.fields.toMap.mapValues(jsonToAny))
      case v: JsArray   ⇒ v.value.map(jsonToAny).toArray
      case v: JsNumber  ⇒ v.value.toLong
      case v: JsString  ⇒ v.value
      case v: JsBoolean ⇒ v.value
      case JsNull       ⇒ null
    }
  }

  /**
   * Build the parameters needed to update ElasticSearch document
   * Parameters contains update script, parameters for the script
   * As null is a valid value to set, in order to remove an attribute an empty array must be used.
   * @param entity entity to update
   * @param updateAttributes contains attributes to update. JSON object contains key (attribute name) and value.
   *   Sub attribute can be updated using dot notation ("attr.subattribute").
   * @return update parameters needed for execution
   */
  private[database] def buildScript(entity: BaseEntity, updateAttributes: JsObject): UpdateParams = {
    import scala.collection.JavaConversions._

    val attrs = updateAttributes.fields.zipWithIndex
    val updateScript = attrs.map {
      case ((name, JsArray(Nil)), index) ⇒
        val names = name.split("\\.")
        names.init.map(n ⇒ s"""["$n"]""").mkString("ctx._source", "", s""".remove("${names.last}")""")
      case ((name, JsNull), index) ⇒
        name.split("\\.").map(n ⇒ s"""["$n"]""").mkString("ctx._source", "", s"=null")
      case ((name, _), index) ⇒
        name.split("\\.").map(n ⇒ s"""["$n"]""").mkString("ctx._source", "", s"=param$index")
    } mkString (";")

    val parameters = jsonToAny(JsObject(attrs.collect {
      case ((name, value), index) if value != JsArray(Nil) && value != JsNull ⇒ s"param$index" → value
    })).asInstanceOf[java.util.Map[String, Any]]

    UpdateParams(entity, updateScript, parameters.toMap, updateAttributes)
  }

  private[database] case class UpdateParams(entity: BaseEntity, updateScript: String, params: Map[String, Any], attributes: JsObject) {
    def updateDef = update(entity.id)
      .in(db.indexName → entity.model.name)
      .routing(entity.routing)
      .script(script(updateScript).params(params))
      .fetchSource(true)
      .retryOnConflict(5)
    def result(attrs: JsObject) =
      entity.model(attrs +
        ("_type" → JsString(entity.model.name)) +
        ("_id" → JsString(entity.id)) +
        ("_routing" → JsString(entity.routing)) +
        ("_parent" → entity.parentId.fold[JsValue](JsNull)(JsString)))
  }

  /**
   * Update entity with new attributes contained in JSON object
   * @param entity entity to update
   * @param updateAttributes contains attributes to update. JSON object contains key (attribute name) and value.
   *   Sub attribute can be updated using dot notation ("attr.subattribute").
   * @return new version of the entity
   */
  def apply(entity: BaseEntity, updateAttributes: JsObject): Future[BaseEntity] = {
    executeScript(buildScript(entity, updateAttributes))
  }

  private[database] def executeScript(params: UpdateParams): Future[BaseEntity] = {
    db.execute(params.updateDef.refresh(RefreshPolicy.IMMEDIATE))
      .map { updateResponse ⇒
        params.result(Json.parse(updateResponse.get.sourceAsString).as[JsObject])
      }
  }
}