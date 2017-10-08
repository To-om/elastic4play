package org.elastic4play.macros

import org.elastic4play.models.{ Model, WithParent }
import scala.reflect.macros.blackbox

class ModelMacro(val c: blackbox.Context)
  extends DatabaseMappingMacro
  with DatabaseReadsMacro
  with DatabaseWritesMacro
  with JsonMacro
  with AttachmentMacro {

  import c.universe._

  /**
   * Create a model from entity type e
   */
  def mkModel[E: WeakTypeTag]: Expr[Model.Base[E]] = {
    val entityType: Type = weakTypeOf[E]
    val className: String = entityType.toString.split("\\.").last
    val modelName: String = Character.toLowerCase(className.charAt(0)) + className.substring(1)


    val withParentType = weakTypeOf[WithParent[_]]
    val parentClass = entityType.typeSymbol.annotations
      .collectFirst {
        case annotation if annotation.tree.tpe <:< withParentType ⇒
          val TypeRef(_, _, List(parentEntityType)) = annotation.tree.tpe
          q"Some(classOf[$parentEntityType])"
      }
      .getOrElse(q"None")

    c.Expr[Model.Base[E]](
      q"""
        import scala.concurrent.{ ExecutionContext, Future }
        import scala.util.{ Failure, Try }
        import org.elastic4play.InternalError
        import org.elastic4play.models.{ Entity, FPath, Model, UpdateOps }
        import org.elastic4play.services.AttachmentSrv

        new Model {
          type E = $entityType

          val name = $modelName
          val parentClass = $parentClass

          val databaseMapping = ${getDatabaseEntityMapping[E]}
          val databaseReads = ${getDatabaseReads[E]}.andMap { (maybeJson, e) ⇒
            maybeJson.fold[Try[$entityType with Entity]](Failure(InternalError(""))) { json ⇒
              ${mkEntityReader[E]}(json, this, e)
            }
          }
          val databaseWrites = ${getDatabaseWrites[E]}

          val writes = ${getEntityJsonWrites[E]}
          override def computedMetrics: Map[String, String] = ???

          def saveAttachment(attachmentSrv: AttachmentSrv, e: E)(implicit ec: ExecutionContext): Future[E] = {
            ${mkAttachmentSaver[E]}(attachmentSrv)(e)
          }

          def saveUpdateAttachment(attachmentSrv: AttachmentSrv, updates: Map[FPath, UpdateOps.Type])(implicit ec: ExecutionContext): Future[Map[FPath, UpdateOps.Type]] = {
            ${mkUpdateAttachmentSaver[E]}(attachmentSrv)(updates)
          }

          val databaseMaps = ${databaseMaps[E]}
        }
      """)
  }
}