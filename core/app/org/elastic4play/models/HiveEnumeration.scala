package org.elastic4play.models

import com.sksamuel.elastic4s.mappings.TextFieldDefinition
import org.elastic4play.InvalidFormatAttributeError
import org.scalactic.{ One, Or }
import play.api.libs.json._

import scala.util.Try

trait HiveEnumeration {
  self: Enumeration ⇒
  type Type

  implicit val databaseMapping = ESFieldMapping[Type](TextFieldDefinition(_).analyzer("keyword").fielddata(true))
  implicit val fieldsParser = FieldsParser.string.map(toString) {
    case s ⇒
      Or.from(Try(getByName(s)))
        .badMap(_ ⇒ One(InvalidFormatAttributeError("", toString, FString(s))))
  }
  val jsonReads: Reads[Value] = Reads((json: JsValue) ⇒ json match {
    case JsString(s) ⇒
      Try(JsSuccess(getByName(s)))
        .orElse(Try(JsSuccess(getByName(s.toLowerCase))))
        .getOrElse(JsError(s"Enumeration expected of type: '$getClass', but it does not appear to contain the value: '$s'"))
    case _ ⇒ JsError("String value expected")
  })

  val jsonWrites: Writes[Value] = Writes((v: Value) ⇒ JsString(v.toString))

  implicit val jsonFormat: Format[Value] = Format(jsonReads, jsonWrites)

  def getByName(name: String): Value = try {
    withName(name)
  }
  catch {
    case _: NoSuchElementException ⇒ //throw BadRequestError(
      sys.error(s"$name is invalid for $toString. Correct values are ${values.mkString(", ")}")
  }
}
