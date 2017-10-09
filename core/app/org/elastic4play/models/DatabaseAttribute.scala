package org.elastic4play.models

import java.util.Date

import com.sksamuel.elastic4s.ElasticDsl.{ booleanField, dateField, intField, longField, objectField }
import com.sksamuel.elastic4s.mappings.{ FieldDefinition, TextFieldDefinition }

import play.api.libs.json._
import scala.util.{ Failure, Success, Try }
import scala.language.implicitConversions

import org.elastic4play.InternalError

case class ESFieldMapping[T](mapping: String ⇒ FieldDefinition)

object ESFieldMapping {
  implicit val stringMapping: ESFieldMapping[String] = ESFieldMapping[String](TextFieldDefinition(_).analyzer("keyword").fielddata(true))
  implicit val textMapping: ESFieldMapping[Text] = ESFieldMapping[Text](TextFieldDefinition(_))

  implicit def seqMapping[T](implicit tMapping: ESFieldMapping[T]): ESFieldMapping[Seq[T]] = ESFieldMapping[Seq[T]](tMapping.mapping)

  implicit val booleanMapping: ESFieldMapping[Boolean] = ESFieldMapping[Boolean](booleanField)
  implicit val longMapping: ESFieldMapping[Long] = ESFieldMapping[Long](longField)
  implicit val intMapping: ESFieldMapping[Int] = ESFieldMapping[Int](intField)
  implicit val attachmentMapping: ESFieldMapping[Attachment] = ESFieldMapping[Attachment](objectField)

  implicit val dateMapping: ESFieldMapping[Date] = ESFieldMapping[Date](dateField)

  implicit def optionMapping[T](implicit tMapping: ESFieldMapping[T]): ESFieldMapping[Option[T]] = ESFieldMapping[Option[T]](tMapping.mapping)

  implicit val metricsMapping: ESFieldMapping[Metrics] = ESFieldMapping[Metrics] { name ⇒
    objectField(name).fields(longField("_default_"))
  }
  implicit val metricsMapMapping: ESFieldMapping[Map[String, Long]] = ESFieldMapping[Map[String, Long]] { name ⇒
    objectField(name).fields(longField("_default_"))
  }
}

case class ESEntityMapping(fields: Map[String, ESFieldMapping[_]]) {
  def toFieldMapping: ESFieldMapping[JsObject] = {
    ESFieldMapping[JsObject] { entityName ⇒
      objectField(entityName).fields(fields.map {
        case (fieldName, fieldMapping) ⇒ fieldMapping.mapping(fieldName)
      })
    }
  }
}

abstract class DatabaseReads[T] extends (DatabaseAdapter.DatabaseFormat ⇒ Try[T]) { dr ⇒
  def andMap[U](f: (DatabaseAdapter.DatabaseFormat, Try[T]) ⇒ Try[U]): DatabaseReads[U] = new DatabaseReads[U] {
    def apply(df: DatabaseAdapter.DatabaseFormat): Try[U] = f(df, dr.apply(df))
  }
}

object DatabaseReads {
  implicit def fromJsonReads[T](r: Reads[T]): DatabaseReads[T] = new DatabaseReads[T] {
    def apply(df: DatabaseAdapter.DatabaseFormat): Try[T] = r.reads(df.getOrElse(JsNull)) match {
      case JsError(errors) ⇒
        val message = errors
          .map {
            case (path, jves) ⇒ s"Database read error on ${path.toJsonString}: ${jves.map(_.messages).mkString(",")}"
          }
          .mkString("\n")
        Failure(InternalError(message))
      case JsSuccess(value, _) ⇒ Success(value)
    }
  }

  implicit def implicitJsonReads[T](implicit r: Reads[T]): DatabaseReads[T] = fromJsonReads(r)

  def apply[T](r: DatabaseAdapter.DatabaseFormat ⇒ Try[T]): DatabaseReads[T] = new DatabaseReads[T] {
    def apply(df: DatabaseAdapter.DatabaseFormat): Try[T] = r(df)
  }
}

abstract class DatabaseWrites[T] extends (T ⇒ Try[DatabaseAdapter.DatabaseFormat]) { dw ⇒
  def optional: DatabaseWrites[Option[T]] = new DatabaseWrites[Option[T]] {
    def apply(ot: Option[T]): Try[DatabaseAdapter.DatabaseFormat] = {
      ot.map(dw.apply)
        .getOrElse(Success(None))
    }
  }
  def sequence: DatabaseWrites[Seq[T]] = new DatabaseWrites[Seq[T]] {
    def apply(ts: Seq[T]): Try[Some[JsArray]] = {
      ts.map(dw.apply)
        .foldLeft[Try[Seq[JsValue]]](Success(Nil)) {
          case (Success(acc), Success(Some(v))) ⇒ Success(acc :+ v)
          case (Failure(f), _)                  ⇒ Failure(f)
          case (_, Failure(f))                  ⇒ Failure(f)
          case (acc, _)                         ⇒ acc
        }
        .map(s ⇒ Some(JsArray(s)))
    }
  }
}

object DatabaseWrites {
  def apply[T](f: T ⇒ Try[DatabaseAdapter.DatabaseFormat]): DatabaseWrites[T] = new DatabaseWrites[T] {
    def apply(t: T): Try[DatabaseAdapter.DatabaseFormat] = f(t)
  }

  implicit def fromJsonWrites[T](w: Writes[T]): DatabaseWrites[T] = new DatabaseWrites[T] {
    def apply(t: T): Try[DatabaseAdapter.DatabaseFormat] = t match {
      case None ⇒ Success(None)
      case _    ⇒ Try(w.writes(t)).map(Option.apply) // TODO check if Some could be used instead of Option
    }
  }

  implicit def implicitJsonWrites[T](implicit w: Writes[T]): DatabaseWrites[T] = fromJsonWrites(w)

  implicit def seqWrites[T](implicit writes: DatabaseWrites[T]): DatabaseWrites[Seq[T]] = new DatabaseWrites[Seq[T]] {
    def apply(t: Seq[T]): Try[DatabaseAdapter.DatabaseFormat] = t.foldLeft[Try[Option[JsArray]]](Success(Some(JsArray.empty))) { (acc, value) ⇒
      for {
        ma ← acc
        mv ← writes(value)
      } yield for (a ← ma; v ← mv) yield (a :+ v)
    }
  }
  implicit def optionWrites[T](implicit writes: DatabaseWrites[T]): DatabaseWrites[Option[T]] = new DatabaseWrites[Option[T]] {
    def apply(t: Option[T]): Try[DatabaseAdapter.DatabaseFormat] = t match {
      case None    ⇒ Success(None)
      case Some(v) ⇒ writes(v)
    }
  }
}

object DatabaseAdapter {
  type FieldMappingDefinition[T] = ESFieldMapping[T]
  type EntityMappingDefinition = ESEntityMapping
  type DatabaseFormat = Option[JsValue]
}