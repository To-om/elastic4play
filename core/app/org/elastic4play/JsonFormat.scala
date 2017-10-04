package org.elastic4play

import play.api.libs.json._

import scala.util.{ Failure, Success, Try }

import org.elastic4play.models.JsonFormat.fieldWrites

object JsonFormat {
  implicit val invalidFormatAttributeErrorWrites = Json.writes[InvalidFormatAttributeError]
  implicit val unknownAttributeErrorWrites = Json.writes[UnknownAttributeError]
  implicit val updateReadOnlyAttributeErrorWrites = Json.writes[UpdateReadOnlyAttributeError]
  implicit val missingAttributeErrorWrites = Json.writes[MissingAttributeError]
  implicit val unsupportedAttributeErrorWrites = Json.writes[UnsupportedAttributeError]

  implicit val attributeErrorWrites = Writes[AttributeError] {
    case ifae: InvalidFormatAttributeError   ⇒ Json.toJson(ifae).as[JsObject] + ("type" → JsString("InvalidFormatAttributeError"))
    case uae: UnknownAttributeError          ⇒ Json.toJson(uae).as[JsObject] + ("type" → JsString("UnknownAttributeError"))
    case uroae: UpdateReadOnlyAttributeError ⇒ Json.toJson(uroae).as[JsObject] + ("type" → JsString("UpdateReadOnlyAttributeError"))
    case mae: MissingAttributeError          ⇒ Json.toJson(mae).as[JsObject] + ("type" → JsString("MissingAttributeError"))
    case uae: UnsupportedAttributeError      ⇒ Json.toJson(uae).as[JsObject] + ("type" → JsString("UnsupportedAttributeError"))
  }

  implicit val attributeCheckingErrorWrites = Json.writes[AttributeCheckingError]

  val datePattern = "yyyyMMdd'T'HHmmssZ"
  implicit val dateFormat = Format(Reads.dateReads(datePattern), Writes.dateWrites(datePattern))

  implicit def tryWrites[A](implicit aWrites: Writes[A]) = Writes[Try[A]] {
    case Success(a) ⇒ aWrites.writes(a)
    case Failure(t) ⇒ JsString(t.getMessage)
  }
}