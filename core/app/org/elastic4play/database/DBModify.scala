package org.elastic4play.database

import javax.inject.{ Inject, Singleton }

import com.sksamuel.elastic4s.ElasticDsl.{ script, update }

import org.elastic4play.models.{ FPath, UpdateOps }
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy
import play.api.Logger
import play.api.libs.json._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class DBModify @Inject() (
  db: DBConfiguration,
  implicit val ec: ExecutionContext) {
  private[DBModify] lazy val logger = Logger(getClass)

  /**
   * Convert JSON value to java native value
   */
  private[database] def jsonToAny(json: JsValue): Any = {
    import scala.collection.JavaConverters.mapAsJavaMapConverter
    json match {
      case v: JsObject  ⇒ v.fields.toMap.mapValues(jsonToAny).asJava
      case v: JsArray   ⇒ v.value.map(jsonToAny).toArray
      case v: JsNumber  ⇒ v.value.toLong
      case v: JsString  ⇒ v.value
      case v: JsBoolean ⇒ v.value
      case JsNull       ⇒ null
    }
  }

  /**
   * Update entity with new attributes contained in JSON object
   *
   * @param modelName        name of the type of the entity
   * @param entityId         Id of the entity to update
   * @param routing          routing information to locate entity
   * @param parent           Id of the parent of the entity
   * @param updateAttributes contains attributes to update.
   * @return new version of the entity
   */
  def apply(modelName: String, entityId: String, routing: String, parent: Option[String], updateAttributes: Map[FPath, UpdateOps.DBType]): Future[JsObject] = {
    import org.elastic4play.models.UpdateOps._
    val (updateScript, params) = updateAttributes
      .zipWithIndex
      .foldLeft((List.empty[String], Map.empty[String, Any])) {
        case ((_updateScript, _params), ((name, SetDBAttribute(Some(JsNull))), _))    ⇒ (s"""ctx._source["$name"]=null""" :: _updateScript, _params)
        case ((_updateScript, _params), ((name, SetDBAttribute(Some(value))), index)) ⇒ (s"""ctx._source["$name"]=params.p$index""" :: _updateScript, _params + (s"p$index" → jsonToAny(value)))
        case ((_updateScript, _params), ((name, UnsetAttribute), _))                  ⇒ (s"""ctx._source.remove("$name")""" :: _updateScript, _params)
        case ((_updateScript, _params), ((_, SetDBAttribute(None)), _))               ⇒ (_updateScript, _params)
      }

    logger.info(s"Updating $modelName $entityId with script: ${updateScript.mkString(";")} ($params)")
    db.execute(update(entityId)
      .in(db.indexName → modelName)
      .routing(routing)
      .script(script(updateScript.mkString(";")).params(params))
      .fetchSource(true)
      .retryOnConflict(5)
      .refresh(RefreshPolicy.IMMEDIATE))
      .map { updateResponse ⇒
        Json.parse(updateResponse.get.sourceAsString).as[JsObject] +
          ("_type" → JsString(modelName)) +
          ("_id" → JsString(entityId)) +
          ("_routing" → JsString(routing)) +
          ("_parent" → parent.fold[JsValue](JsNull)(JsString))
      }
  }
}