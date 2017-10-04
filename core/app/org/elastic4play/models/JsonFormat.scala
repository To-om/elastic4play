package org.elastic4play.models

import java.nio.file.{ Path, Paths }

import play.api.libs.json._

object JsonFormat {

  val pathReads = Reads[Path] { json ⇒ json.validate[String].map(filepath ⇒ Paths.get(filepath)) }
  val pathWrites = Writes[Path]((path: Path) ⇒ JsString(path.toString))
  implicit val pathFormat = Format[Path](pathReads, pathWrites)

  implicit val fieldWrites = Writes[Field] { field ⇒ JsString(field.toString) } // FIXME
  //  val stringInputValueWrites = Writes[StringInputValue] { siv ⇒
  //    if (siv.data.size == 1) JsString(siv.data.head)
  //    else Json.arr(siv.data)
  //  }
  //  val jsonInputValueWrites = Writes[JsonInputValue](_.data)
  //  val fileInputValueWrites = Json.writes[FileInputValue]
  //  val attachmentInputValueWrites = Json.writes[AttachmentInputValue]
  //  //  implicit val nullInputValue =
  //
  //  implicit val inputValueWrites = Writes[InputValue] {
  //    case siv: StringInputValue     ⇒ Json.toJson(siv)(stringInputValueWrites)
  //    case jiv: JsonInputValue       ⇒ Json.toJson(jiv)(jsonInputValueWrites)
  //    case fiv: FileInputValue       ⇒ Json.toJson(fiv)(fileInputValueWrites)
  //    case aiv: AttachmentInputValue ⇒ Json.toJson(aiv)(attachmentInputValueWrites)
  //  }

  //  implicit class RichJsLookup(lookup: JsLookupResult) {
  //    def toOr[A](implicit reads: Reads[A]) = {
  //      lookup.validate[A] match {
  //        case JsSuccess(value, path) => Good(value)
  //        case JsError(errors) => errors.map {
  //          case (JsPath(paths), validationErrors) =>
  //            paths.map {
  //              case IdxPathNode(index)   =>
  //              case KeyPathNode(key)     =>
  //              case RecursiveSearch(key) =>
  //            }
  //            ??? //Bad(One(e))
  //        }
  //      }
  //    }
  //  }
  def merge[A, B, C](jra: JsResult[A], jrb: JsResult[B])(f: (A, B) ⇒ C): JsResult[C] = {
    (jra, jrb) match {
      case (JsSuccess(a, _), JsSuccess(b, _)) ⇒ JsSuccess(f(a, b))
      case (JsError(e1), JsError(e2))         ⇒ JsError(JsError.merge(e1, e2))
      case (JsError(e), _)                    ⇒ JsError(e)
      case (_, JsError(e))                    ⇒ JsError(e)
    }
  }

  //implicit def updateOpsWrites[T] = Writes[UpdateOps.Type] { _ => ???  }

  //  val entityWrites = OWrites[Entity] { entity ⇒
  //    val model = entity._model
  //    model.toOutput(entity.asInstanceOf[model.E with Entity])
  //  }
}