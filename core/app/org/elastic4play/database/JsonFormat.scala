package org.elastic4play.database

import com.sksamuel.elastic4s.searches.RichSearchHit
import org.elasticsearch.search.SearchHit
import play.api.libs.json._

object JsonFormat {
  // TODO duplicated with org.elastic4play.database.DBFind.hit2json
  implicit val richSearchHitWrites = OWrites[RichSearchHit] { hit ⇒
    val fieldsValue = hit.fields
    val id = JsString(hit.id)
    Option(hit.sourceAsString).filterNot(_ == "").fold(JsObject(Nil))(s ⇒ Json.parse(s).as[JsObject]) +
      ("_type" → JsString(hit.`type`)) +
      ("_routing" → fieldsValue.get("_routing").fold(id)(r ⇒ JsString(r.java.getValue[String]))) +
      ("_parent" → fieldsValue.get("_parent").fold[JsValue](JsNull)(r ⇒ JsString(r.java.getValue[String]))) +
      ("_id" → id)
  }
  implicit val searchHitWrites = OWrites[SearchHit] { hit ⇒ richSearchHitWrites.writes(RichSearchHit(hit)) }
}