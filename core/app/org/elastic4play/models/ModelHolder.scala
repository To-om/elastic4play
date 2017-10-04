package org.elastic4play.models

import play.api.libs.json._

abstract class ModelHolder[E] {
  //    extends JsonFormats
  //    with ToOutputs {
  val model: Model.Base[E]
  //  implicit val entityFormat = model.writes
  //  implicit val entityMapping: ESFieldMapping[E] = ESFieldMapping[model.E](objectField)
  //  implicit lazy val entityInputParser = InputParser(model.name) {
  //    case Some(jiv @ JsonInputValue(json)) ⇒ json.asOpt[E with Entity] match {
  //      case Some(e) ⇒ Good(e)
  //      case None    ⇒ Bad(One(InvalidFormatAttributeError(model.name, model.name, jiv)))
  //    }
  //  }
  //def apply(record: Record[model.NA]): E
}

//trait ToOutputs {
//  implicit def toOutputFromWrites[T](implicit d: Writes[T]): ToOutput[T] = ToOutput[T]
//  implicit def optionalOutput[T](implicit to: ToOutput[T]) = new ToOutput[Option[T]] {
//    def apply(t: Option[T]): JsValue = t.fold[JsValue](JsNull)(to)
//  }
//  //  implicit val stringToOutput = ToOutput[String]
//  //  implicit val intToOutput = ToOutput[Int]
//  //  implicit val longToOutput = ToOutput[Long]
//  //  implicit val attachmentToOutput = ToOutput[Attachment]
//}

trait JsonFormats {
  implicit def optionWithNull[T](implicit rds: Reads[T]): Reads[Option[T]] = Reads.optionWithNull
  implicit def optionFormat[T](implicit format: Format[T]) = Format(Reads.optionWithNull(format), Writes.optionWithNull(format))
  def seqReads[T](implicit reads: Reads[T]): Reads[Seq[T]] = Reads[Seq[T]] {
    case JsNull ⇒ JsSuccess(Nil)
    case js     ⇒ Reads.traversableReads[Seq, T].reads(js)
  }
  //  implicit val textFormat = Format(Reads.StringReads.map(_.asInstanceOf[Text]), Writes[Text](JsString))
  implicit def seqFormat[T](implicit format: Format[T]) = Format(seqReads(format), Writes.seq(format))
  //implicit val attachmentFormat = Json.format[Attachment]

  //  implicit def entityFormat[M <: Model](record: Record[M#CA]) = {
  //    e._model.toOutput()
  //  }
  //implicit def entityFormat[E](e: E): Format[E] = macro EntityMacros.jsonFormat[E]

}