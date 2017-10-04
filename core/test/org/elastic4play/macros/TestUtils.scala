package org.elastic4play.macros

import org.elastic4play.models.{ DatabaseReads, DatabaseWrites, Entity, FPath, FieldsParser, Model, UpdateFieldsParser }
import org.elastic4play.services.AttachmentSrv
import play.api.libs.json.{ JsValue, OWrites }

import scala.concurrent.Future
import scala.language.experimental.macros
import scala.util.Try

trait TestUtils {
  def getFieldsParser[T]: FieldsParser[T] = macro FieldsParserMacro.getFieldsParser[T]
  def getUpdateFieldsParser[T]: UpdateFieldsParser[T] = macro FieldsParserMacro.getUpdateFieldsParser[T]

  def getDatabaseWrites[T]: DatabaseWrites[T] = macro ModelMacro.getDatabaseWrites[T]
  def getDatabaseReads[T]: DatabaseReads[T] = macro ModelMacro.getDatabaseReads[T]
  def getEntityJsonWrites[T]: OWrites[T with Entity] = macro ModelMacro.getEntityJsonWrites[T]
  def mkEntityReader[T]: (JsValue, Model, Try[T]) ⇒ Try[T with Entity] = macro ModelMacro.mkEntityReader[T]
  def mkAttachSaver[T]: AttachmentSrv ⇒ T ⇒ Future[T] = macro ModelMacro.mkAttachmentSaver[T]
  def databaseMaps[T]: Map[FPath, DatabaseWrites[_]] = macro ModelMacro.databaseMaps[T]
}