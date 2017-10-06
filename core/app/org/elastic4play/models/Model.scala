package org.elastic4play.models

import java.util.Date

import scala.annotation.StaticAnnotation
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.experimental.macros

import play.api.libs.json._

import com.sksamuel.elastic4s.mappings.FieldDefinition

import org.elastic4play.macros.ModelMacro
import org.elastic4play.services.AttachmentSrv

class WithParent[T] extends StaticAnnotation
class WithParser[T](fieldsParser: FieldsParser[T]) extends StaticAnnotation
class WithUpdateParser[T](fieldsParsers: UpdateFieldsParser[T]) extends StaticAnnotation
class WithOutput[T](writes: Writes[T]) extends StaticAnnotation
class WithDatabase[T](reads: DatabaseReads[T], writes: DatabaseWrites[T]) extends StaticAnnotation
class WithFieldMapping[T](mapping: DatabaseAdapter.FieldMappingDefinition[T]) extends StaticAnnotation {
  def this(fieldDefinition: FieldDefinition) = this(ESFieldMapping(_ ⇒ fieldDefinition).asInstanceOf[DatabaseAdapter.FieldMappingDefinition[T]])
}
class WithEntityMapping(mapping: DatabaseAdapter.EntityMappingDefinition) extends StaticAnnotation

trait Entity {
  val _id: String
  val _routing: String
  val _parent: Option[String]
  val _model: Model
  val _createdBy: String
  val _updatedBy: Option[String]
  val _createdAt: Date
  val _updatedAt: Option[Date]
  def toJson: JsObject = _model.writes.writes(this.asInstanceOf[_model.E with Entity]).as[JsObject]
}

object Model {
  type Base[E0] = Model {
    type E = E0
  }
  def apply[E]: Model.Base[E] = macro ModelMacro.mkModel[E]
}

abstract class Model {
  type E

  val name: String
  val parents: Seq[Model]

  val databaseMapping: Map[String, DatabaseAdapter.EntityMappingDefinition]
  val databaseReads: DatabaseReads[E with Entity]
  val databaseWrites: DatabaseWrites[E]

  val writes: OWrites[E with Entity]
  def toOutput(e: E with Entity): JsObject = writes.writes(e)

  def computedMetrics: Map[String, String] = ???

  def saveAttachment(attachmentSrv: AttachmentSrv, e: E)(implicit ec: ExecutionContext): Future[E]
  def saveUpdateAttachment(attachmentSrv: AttachmentSrv, updates: Map[FPath, UpdateOps.Type])(implicit ec: ExecutionContext): Future[Map[FPath, UpdateOps.Type]]
  val databaseMaps: Map[FPath, DatabaseWrites[_]] //(updates: Map[FPath, UpdateOps.Type]): Map[FPath, UpdateOps.DBType]
}