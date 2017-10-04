package org.elastic4play.models

import java.nio.file.Path

import org.elastic4play.utils.Hash
import scala.collection.immutable

import play.api.Logger
import play.api.libs.json._
import play.api.mvc._

sealed trait Field {
  def get(pathElement: String): Field = FUndefined
  def set(path: FPath, field: Field): Field = if (path.isEmpty) field else sys.error(s"$this.set($path, $field)")
}

object Field {
  private[Field] lazy val logger = Logger(getClass)
  def apply(json: JsValue): Field = json match {
    case JsString(s)  ⇒ FString(s)
    case JsNumber(n)  ⇒ FNumber(n.toLong)
    case JsBoolean(b) ⇒ FBoolean(b)
    case JsObject(o)  ⇒ FObject(o.mapValues(Field.apply).toMap)
    case JsArray(a)   ⇒ FSeq(a.map(Field.apply))
    case JsNull       ⇒ FNull
  }
  def apply(request: Request[AnyContent]): Field = {
    def queryFields: FObject = FObject(request
      .queryString
      .filterNot(_._1.isEmpty())
      .mapValues(FAny.apply))

    request.body match {
      case AnyContentAsFormUrlEncoded(data) ⇒ FObject(data.mapValues(v ⇒ FAny(v))) ++ queryFields
      case AnyContentAsText(txt) ⇒
        logger.warn(s"Request body has unrecognized format (text), it is ignored:\n$txt")
        queryFields
      case AnyContentAsXml(xml) ⇒
        logger.warn(s"Request body has unrecognized format (xml), it is ignored:\n$xml")
        queryFields
      case AnyContentAsJson(json: JsObject) ⇒ Field(json).asInstanceOf[FObject] ++ queryFields
      case AnyContentAsMultipartFormData(MultipartFormData(dataParts, files, badParts)) ⇒
        if (badParts.nonEmpty)
          logger.warn("Request body contains invalid parts")
        val dataFields = dataParts
          .getOrElse("_json", Nil)
          .headOption
          .map { s ⇒
            Json.parse(s).as[JsObject]
              .value.toMap
              .mapValues(Field.apply)
          }
          .getOrElse(Map.empty)
        files.foldLeft(queryFields ++ FObject(dataFields)) {
          case (obj, MultipartFormData.FilePart(key, filename, contentType, file)) ⇒
            obj.set(FPath(key), FFile(filename.split("[/\\\\]").last, file, contentType.getOrElse("application/octet-stream")))
        }
      case AnyContentAsRaw(raw) ⇒
        if (raw.size > 0)
          logger.warn(s"Request $request has unrecognized body format (raw), it is ignored:\n$raw")
        queryFields
      case AnyContentAsEmpty ⇒ queryFields
      case other ⇒
        sys.error(s"invalid request body : $other (${other.getClass})")
    }
  }
}
sealed trait Attachment
object Attachment {
  val attachmentWrites = Writes[Attachment] {
    case attachment: FAttachment ⇒
      Json.obj(
        "name" → attachment.name,
        "hashes" → attachment.hashes,
        "size" → attachment.size,
        "contentType" → attachment.contentType,
        "id" → attachment.id)
  }
  // FIXME if attachment is a FFile
  val attachmentReads = Reads[Attachment] { json ⇒
    for {
      name ← (json \ "name").validate[String]
      hashes ← (json \ "hashes").validate[Seq[Hash]]
      size ← (json \ "size").validate[Long]
      contentType ← (json \ "contentType").validate[String]
      id ← (json \ "id").validate[String]
    } yield FAttachment(name, hashes, size, contentType, id)
  }

  implicit val attachmentFormat = Format(attachmentReads, attachmentWrites)
}
//case class FPath(elem: FPathElem.Type) extends AnyVal {
//  def /(p: FPath): FPath = {
//    if (p.isEmpty) this
//    else FPath(elem / p.elem)
//  }
//  def /(p: String): FPath = this / FPath(p)
//
//  def toSeq: FPath = FPath(elem.toSeq)
//  def toSeq(index: Int): FPath = FPath(elem.toSeq(index))
//  //
//  //  def isSeq: Boolean = path.startsWith("[]")
//  //  //  def /(p: FieldsPath): FieldsPath = /(p.toString)
//  //  def apply(index: Int) = FPath(s"$path[$index]")
//
//  override def toString: String = elem.toString
//  def isEmpty = elem.isEmpty
//}

case class FString(value: String) extends Field
case class FNumber(value: Long) extends Field
case class FBoolean(value: Boolean) extends Field
case class FSeq(values: Seq[Field]) extends Field {
  override def set(path: FPath, field: Field): Field = path match {
    case FPathSeq(_, FPathEmpty)        ⇒ FSeq(values :+ field)
    case FPathElemInSeq(_, index, tail) ⇒ FSeq(values.patch(index, Seq(values.applyOrElse(index, (_: Int) ⇒ FUndefined).set(tail, field)), 1))
  }
}
object FSeq {
  def apply(value1: Field, values: Field*): FSeq = new FSeq(value1 +: values)
  def apply() = new FSeq(Nil)
}
case object FNull extends Field
case object FUndefined extends Field
case class FAny(value: Seq[String]) extends Field
case class FFile(filename: String, filepath: Path, contentType: String) extends Field with Attachment
case class FAttachment(name: String, hashes: Seq[Hash], size: Long, contentType: String, id: String) extends Field with Attachment

object FObject {
  def empty = new FObject(Map.empty)
  def apply(elems: (String, Field)*): FObject = new FObject(Map(elems: _*))
  def apply(map: Map[String, Field]): FObject = new FObject(map)
  def apply(o: JsObject): FObject = new FObject(o.value.mapValues(Field.apply).toMap)
}
case class FObject(fields: immutable.Map[String, Field]) extends Field { // Map[String, Field] with immutable.MapLike[String, Field, FObject] with
  def empty: FObject = FObject.empty
  def iterator: Iterator[(String, Field)] = fields.iterator
  def +(kv: (String, Field)): FObject = new FObject(fields + kv)
  def -(k: String) = new FObject(fields - k)
  def ++(other: FObject): FObject = new FObject(fields ++ other.fields)
  override def set(path: FPath, field: Field): FObject = {
    path match {
      case FPathElem(p, tail) ⇒ fields.get(p) match {
        case Some(FSeq(_))        ⇒ sys.error(s"$this.set($path, $field)")
        case Some(f)              ⇒ FObject(fields.updated(p, f.set(tail, field)))
        case None if tail.isEmpty ⇒ FObject(fields.updated(p, field))
        case None                 ⇒ FObject(fields.updated(p, FObject().set(tail, field)))
      }
      case FPathSeq(p, tail) if tail.isEmpty ⇒ fields.get(p) match {
        case Some(FSeq(s)) ⇒ FObject(fields.updated(p, FSeq(s :+ field)))
        case None          ⇒ FObject(fields.updated(p, FSeq(Seq(field))))
        case _             ⇒ sys.error(s"$this.set($path, $field)")
      }
      case FPathElemInSeq(p, idx, tail) ⇒ fields.get(p) match {
        case Some(FSeq(s)) if s.isDefinedAt(idx) ⇒ FObject(fields.updated(p, FSeq(s.patch(idx, Seq(s(idx).set(tail, field)), 1))))
        case Some(FSeq(s)) if s.length == idx    ⇒ FObject(fields.updated(p, FSeq(s :+ field)))
        case _                                   ⇒ sys.error(s"$this.set($path, $field)")
      }
      case _ ⇒ sys.error(s"$this.set($path, $field)")

      //FObject(fields.updated(p, fields.get(p).fold(field)(_.set(tail, field))))
      //FObject(fields.updated(p, fields.getOrElse(p, FUndefined).set(tail, field)))
      //case FPathEmpty         ⇒ sys.error(s"$this.set($path, $field)")
    }
  }
  //  def subFields(prefix: String): Fields = {
  //    Fields(fields
  //      .collect {
  //        case (k, v) if k.startsWith(s"$prefix.")     ⇒ Seq(k.drop(prefix.length + 1) → v)
  //        case (`prefix`, JsonInputValue(JsObject(f))) ⇒ f.mapValues(JsonInputValue).toSeq
  //      }
  //      .flatten
  //      .toMap)
  //  }
  //
  //  def addPrefix(prefix: String): Fields = {
  //    val subObject = fields.collect {
  //      case (k, JsonInputValue(j))        ⇒ k → j
  //      case (k, StringInputValue(Seq(s))) ⇒ k → JsString(s)
  //      case (k, StringInputValue(s))      ⇒ k → JsArray(s.map(JsString))
  //    }
  //    val newFields = fields.filter {
  //      case (_, _: FileInputValue)       ⇒ true
  //      case (_, _: AttachmentInputValue) ⇒ true
  //      case other                        ⇒ false
  //    } + (prefix → JsonInputValue(JsObject(subObject)))
  //    new Fields(newFields)
  //  }
  //
  override def get(path: String): Field = fields.getOrElse(path, FUndefined)
  //
  //  def filter(f: String ⇒ Boolean): Map[String, InputValue] = fields.filterKeys(f)
  //
  //  /**
  //   * Extract all fields, name and value
  //   */
  //  //def map[A](f: ((String, InputValue)) ⇒ A) = fields.map(f)
  //
  //  def iterator: Iterator[(String, InputValue)] = fields.iterator
  //
  //  /**
  //   * Returns a copy of this class with a new field (or replacing existing field)
  //   */
  //  def set(name: String, value: InputValue): Fields = {
  //    //    (value, fields.get(name)) match {
  //    //      case (_, None) => new Fields(fields + (name → value))
  //    //      case (StringInputValue(s1), Some(StringInputValue(s2)) => new Fields(fields + (name → StringInputValue
  //    //    }
  //    new Fields(fields + (name → value))
  //  }
  //
  //  /**
  //   * Returns a copy of this class with a new field (or replacing existing field)
  //   */
  //  def set(name: String, value: String): Fields = set(name, StringInputValue(Seq(value)))
  //
  //  /**
  //   * Returns a copy of this class with a new field (or replacing existing field)
  //   */
  //  def set(name: String, value: JsValue): Fields = set(name, JsonInputValue(value))
  //
  //  /**
  //   * Returns a copy of this class with a new field if value is not None otherwise returns this
  //   */
  //  def set(name: String, value: Option[JsValue]): Fields = value.fold(this)(v ⇒ set(name, v))
  //
  //  /**
  //   * Return a copy of this class without the specified field
  //   */
  //  def unset(name: String): Fields = new Fields(fields - name)
  //  def unset(path: FieldsPath): Fields = ???
  //
  //  // def isEmpty = fields.isEmpty
  //
  //  def ++(other: GenTraversableOnce[(String, InputValue)]) = new Fields(fields ++ other)
  //
  //  def toJsObject: Option[JsObject] = fields
  //    .foldLeft[Option[Seq[(String, JsObject)]]](Some(Nil)) {
  //      case (Some(l), (name, JsonInputValue(v: JsObject))) ⇒ Some((name → v) +: l)
  //      case _                                              ⇒ None
  //    }
  //    .map(JsObject.apply)
  //
  //  override def toString: String = fields.toString()
  //
  //  override def canEqual(other: Any): Boolean = other.isInstanceOf[Fields]
  //
  //  override def equals(other: Any): Boolean = other match {
  //    case that: Fields ⇒
  //      (that canEqual this) &&
  //        fields == that.fields
  //    case _ ⇒ false
  //  }
  //
  //  override def hashCode(): Int = {
  //    val state = Seq(fields)
  //    state.map(_.hashCode()).foldLeft(0)((a, b) ⇒ 31 * a + b)
  //  }
  //}
  //
  //object Fields {
  //  val empty: Fields = new Fields(Map.empty[String, InputValue])
  //
  //  /**
  //   * Create an instance of Fields from a JSON object
  //   */
  //  def apply(obj: JsObject): Fields = {
  //    val fields = obj.value.mapValues(v ⇒ JsonInputValue(v))
  //    new Fields(fields.toMap)
  //  }
  //
  //  def apply(fields: Map[String, InputValue]): Fields = {
  //    if (fields.keysIterator.exists(_.startsWith("_")))
  //      throw BadRequestError("Field starting with '_' is forbidden")
  //    new Fields(fields)
  //  }
  //
  //  def apply(inputValue: InputValue): Fields = ???
  //}

}

/**
 * Define a data value from HTTP request (or from service layer for AttachmentInputValue). It can be simple string,
 * json, file, attachment or null (maybe xml in future)
 */
//sealed trait InputValue {
//  def jsonValue: JsValue
//}

///**
// * An attachment
// */
//
//
//object Attachment {
//  val attachmentWrites = Writes[Attachment] {
//    case aiv: AttachmentInputValue ⇒ AttachmentInputValue.attachmentInputValueFormat.writes(aiv)
//    case other                     ⇒ sys.error(s"TODO ${other.getClass}")
//  }
//  val attachmentReads: Reads[Attachment] =
//    AttachmentInputValue
//      .attachmentInputValueFormat
//      .map(_.asInstanceOf[Attachment])
//
//  implicit val attachmentFormat = Format(attachmentReads, attachmentWrites)
//}
//
///**
// * Define a data value from HTTP request as simple string
// */
//case class StringInputValue(data: Seq[String]) extends InputValue {
//  def jsonValue: JsValue = Json.toJson(data)
//}
//
//object StringInputValue {
//  def apply(s: String): StringInputValue = this(Seq(s))
//}
//
///**
// * Define a data value from HTTP request as json value
// */
//case class JsonInputValue(data: JsValue) extends InputValue {
//  def jsonValue: JsValue = data
//}
//
///**
// * Define a data value from HTTP request as file (filename, path to temporary file and content type). Other data are lost
// */
//case class FileInputValue(name: String, filepath: Path, contentType: String) extends InputValue with Attachment {
//  def jsonValue: JsObject = Json.obj("name" → name, "filepath" → filepath, "contentType" → contentType)
//}
//
///**
// * Define an attachment that is already in datastore. This type can't be from HTTP request.
// */
//case class AttachmentInputValue(name: String, hashes: Seq[Hash], size: Long, contentType: String, id: String) extends InputValue with Attachment {
//  def jsonValue: JsObject = Json.obj(
//    "name" → name,
//    "hashes" → hashes,
//    "size" → size,
//    "contentType" → contentType,
//    "id" → id)
//
//  //def toSavedAttachment = SavedAttachment(name, hashes, size, contentType, id)
//}
//object AttachmentInputValue {
//  implicit val attachmentInputValueFormat = Json.format[AttachmentInputValue]
//}
//
////object AttachmentInputValue {
////  def apply(attachment: SavedAttachment) = new AttachmentInputValue(attachment.name, attachment.hashes, attachment.size, attachment.contentType, attachment.id)
////}
//
///**
// * Define a data value from HTTP request as null (empty value)
// */
////object NullInputValue extends InputValue {
////  def jsonValue: JsValue = JsNull
////}
//
//
//
///**
// * Expr[RecordOps[_] ⇒ FieldOps[_]]
// * Contain data values from HTTP request
// */
//class Fields(private val fields: Map[String, InputValue]) extends Iterable[(String, InputValue)] {
//
//  def subFields(prefix: String): Fields = {
//    Fields(fields
//      .collect {
//        case (k, v) if k.startsWith(s"$prefix.")     ⇒ Seq(k.drop(prefix.length + 1) → v)
//        case (`prefix`, JsonInputValue(JsObject(f))) ⇒ f.mapValues(JsonInputValue).toSeq
//      }
//      .flatten
//      .toMap)
//  }
//
//  def addPrefix(prefix: String): Fields = {
//    val subObject = fields.collect {
//      case (k, JsonInputValue(j))        ⇒ k → j
//      case (k, StringInputValue(Seq(s))) ⇒ k → JsString(s)
//      case (k, StringInputValue(s))      ⇒ k → JsArray(s.map(JsString))
//    }
//    val newFields = fields.filter {
//      case (_, _: FileInputValue)       ⇒ true
//      case (_, _: AttachmentInputValue) ⇒ true
//      case other                        ⇒ false
//    } + (prefix → JsonInputValue(JsObject(subObject)))
//    new Fields(newFields)
//  }
//
//  def get(path: FieldsPath): Option[InputValue] = ???
//  def get(name: String): Option[InputValue] = fields.get(name)
//
//  def filter(f: String ⇒ Boolean): Map[String, InputValue] = fields.filterKeys(f)
//
//  /**
//   * Extract all fields, name and value
//   */
//  //def map[A](f: ((String, InputValue)) ⇒ A) = fields.map(f)
//
//  def iterator: Iterator[(String, InputValue)] = fields.iterator
//
//  /**
//   * Returns a copy of this class with a new field (or replacing existing field)
//   */
//  def set(name: String, value: InputValue): Fields = {
//    //    (value, fields.get(name)) match {
//    //      case (_, None) => new Fields(fields + (name → value))
//    //      case (StringInputValue(s1), Some(StringInputValue(s2)) => new Fields(fields + (name → StringInputValue
//    //    }
//    new Fields(fields + (name → value))
//  }
//
//  /**
//   * Returns a copy of this class with a new field (or replacing existing field)
//   */
//  def set(name: String, value: String): Fields = set(name, StringInputValue(Seq(value)))
//
//  /**
//   * Returns a copy of this class with a new field (or replacing existing field)
//   */
//  def set(name: String, value: JsValue): Fields = set(name, JsonInputValue(value))
//
//  /**
//   * Returns a copy of this class with a new field if value is not None otherwise returns this
//   */
//  def set(name: String, value: Option[JsValue]): Fields = value.fold(this)(v ⇒ set(name, v))
//
//  /**
//   * Return a copy of this class without the specified field
//   */
//  def unset(name: String): Fields = new Fields(fields - name)
//  def unset(path: FieldsPath): Fields = ???
//
//  // def isEmpty = fields.isEmpty
//
//  def ++(other: GenTraversableOnce[(String, InputValue)]) = new Fields(fields ++ other)
//
//  def toJsObject: Option[JsObject] = fields
//    .foldLeft[Option[Seq[(String, JsObject)]]](Some(Nil)) {
//      case (Some(l), (name, JsonInputValue(v: JsObject))) ⇒ Some((name → v) +: l)
//      case _                                              ⇒ None
//    }
//    .map(JsObject.apply)
//
//  override def toString: String = fields.toString()
//
//  override def canEqual(other: Any): Boolean = other.isInstanceOf[Fields]
//
//  override def equals(other: Any): Boolean = other match {
//    case that: Fields ⇒
//      (that canEqual this) &&
//        fields == that.fields
//    case _ ⇒ false
//  }
//
//  override def hashCode(): Int = {
//    val state = Seq(fields)
//    state.map(_.hashCode()).foldLeft(0)((a, b) ⇒ 31 * a + b)
//  }
//}
//
//object Fields {
//  val empty: Fields = new Fields(Map.empty[String, InputValue])
//
//  /**
//   * Create an instance of Fields from a JSON object
//   */
//  def apply(obj: JsObject): Fields = {
//    val fields = obj.value.mapValues(v ⇒ JsonInputValue(v))
//    new Fields(fields.toMap)
//  }
//
//  def apply(fields: Map[String, InputValue]): Fields = {
//    if (fields.keysIterator.exists(_.startsWith("_")))
//      throw BadRequestError("Field starting with '_' is forbidden")
//    new Fields(fields)
//  }
//
//  def apply(inputValue: InputValue): Fields = ???
//}
