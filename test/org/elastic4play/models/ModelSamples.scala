package org.elastic4play.models

import play.api.libs.json.{ JsNull, JsString, Reads, Writes }

import org.scalactic.Good

object ModelSamples {
  val hobbiesParser: FieldsParser[Seq[String]] = FieldsParser("hobbies") {
    case (_, FString(s)) ⇒ Good(s.split(",").toSeq)
  }
  val hobbiesDatabaseReads: Reads[Seq[String]] = Reads[Seq[String]](json ⇒ json.validate[String].map(_.split(",").toSeq))
  val hobbiesDatabaseWrites: Writes[Seq[String]] = Writes[Seq[String]](h ⇒ JsString(h.mkString(",")))
  //val hobbiesDatabaseFormat: Format[Seq[String]] = Format[Seq[String]](hobbiesDatabaseReads, hobbiesDatabaseWrites)
  val certificateOutput = Writes[Option[Attachment]] {
    case Some(FAttachment(name, hashes, size, contentType, id)) ⇒ JsString(s"Certificate $name with id $id")
    case None                                                   ⇒ JsNull
    case a                                                      ⇒ sys.error(s"Try to output an attachment which is not an AttachmentInputValue (${a.getClass}). Should never happen")
  }
}