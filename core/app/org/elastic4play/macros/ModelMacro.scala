package org.elastic4play.macros

import org.elastic4play.models.{ Model, WithParent }
import scala.reflect.macros.blackbox

class ModelMacro(val c: blackbox.Context)
  extends DatabaseMappingMacro
  with DatabaseReadsMacro
  with DatabaseWritesMacro
  with AttachmentMacro {

  import c.universe._

  /**
   * This macro build a method that takes a json object, a model and an object E (a try of) and returns an Entity E
   * with Entity members filled by json object member and by the provided model.
   *
   * @tparam E type of the object
   * @return a method that returns an E with Entity
   */
  def mkEntityReader[E: WeakTypeTag]: Tree = {
    val eType = weakTypeOf[E]
    eType match {
      case CaseClassType(symbols @ _*) ⇒
        val params = symbols.map(p ⇒ q"e.${TermName(p.name.toString)}")
        val id = if (symbols.exists(_.name.toString == "_id")) q"()" else q"""val _id = (json \ "_id").as[String]"""
        q"""
       import scala.util.Try
       import play.api.libs.json.JsValue
       import org.elastic4play.models.{ Entity, Model }

       (json: JsValue, model: Model, te: Try[$eType]) ⇒
         te.map { e ⇒
           new $eType(..$params) with Entity {
             $id
             val _routing = (json \ "_routing").as[String]
             val _parent = (json \ "_parent").asOpt[String]
             val _model = model
             val _createdBy = (json \ "_createdBy").as[String]
             val _createdAt = (json \ "_createdAt").as[java.util.Date]
             val _updatedBy = (json \ "_updatedBy").asOpt[String]
             val _updatedAt = (json \ "_updatedAt").asOpt[java.util.Date]
           }
         }
    """
    }
  }

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