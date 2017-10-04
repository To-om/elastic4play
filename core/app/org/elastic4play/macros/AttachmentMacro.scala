package org.elastic4play.macros

import scala.reflect.macros.blackbox

import org.elastic4play.models.Attachment

trait AttachmentMacro {
  val c: blackbox.Context

  import c.universe._

  /**
   * Create a function that save all attachment of an E
   * @tparam E
   * @return
   * @usecase def mkAttachmentSaver[E]: Expr[AttachmentSrv] ⇒ Expr[E] ⇒ Expr[Future[E]\]
   * :K
   */
  // TODO save attachment in subfields
  def mkAttachmentSaver[E: WeakTypeTag]: Tree = {
    val eTpe = weakTypeOf[E]
    val saver = eTpe.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.head
      .foldLeft[Tree](q"(e: $eTpe) ⇒ scala.concurrent.Future.successful(e)") {
        case (fe, element) if element.asTerm.typeSignature <:< typeOf[Attachment] ⇒
          val elementName = element.asTerm.name
          q"""
            import scala.concurrent.Future
            import org.elastic4play.models.{ FAttachment, FFile }

            $fe.andThen { futureE ⇒
              futureE.flatMap(e ⇒ e.$elementName match {
                case f: FFile       ⇒ attachmentSrv.save(f).map { a ⇒ e.copy($elementName = a) }
                case _: FAttachment ⇒ Future.successful(e)
              })
            }
        """
        case (fe, element) if element.asTerm.typeSignature <:< typeOf[Option[Attachment]] ⇒
          val elementName = element.asTerm.name
          q"""
           import scala.concurrent.Future
           import org.elastic4play.models.{ FAttachment, FFile }

           $fe.andThen { futureE ⇒
              futureE.flatMap { e ⇒
                e.$elementName.fold(Future.successful(e)) {
                  case f: FFile       ⇒ attachmentSrv.save(f).map { a ⇒ e.copy($elementName = Some(a)) }
                  case _: FAttachment ⇒ Future.successful(e)
                }
              }
            }
            """
        case (fe, _) ⇒ fe
      }
    q"(attachmentSrv: org.elastic4play.services.AttachmentSrv) ⇒ $saver"
  }

  // TODO save attachment in subfields
  def mkUpdateAttachmentSaver[E: WeakTypeTag]: Tree = {
    val eTpe = weakTypeOf[E]
    val saver = eTpe.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.head
      .foldLeft[Tree](q"(ops: Map[org.elastic4play.models.FPath, org.elastic4play.models.UpdateOps.Type]) ⇒ scala.concurrent.Future.successful(ops)") {
        case (fo, element) if element.asTerm.typeSignature <:< typeOf[Attachment] ⇒
          val elementName = element.asTerm.name.toString
          q"""
            import org.elastic4play.models.UpdateOps.SetAttribute

            $fo.andThen { futureO ⇒
              futureO.flatMap { o ⇒
                val path = FPath($elementName)
                o.get(path)
                  .collect {
                    case SetAttribute(file: FFile) ⇒ attachmentSrv.save(file).map(attachment ⇒ o.updated(path, SetAttribute(attachment)))
                  }
                  .getOrElse(Future.successful(o))
              }
            }
         """
        case (fo, element) if element.asTerm.typeSignature <:< typeOf[Option[Attachment]] ⇒
          val elementName = element.asTerm.name.toString
          q"""
            import org.elastic4play.models.UpdateOps.SetAttribute

            $fo.andThen { futureO ⇒
              futureO.flatMap { o ⇒
                val path = FPath($elementName)
                o.get(path)
                  .collect {
                    case SetAttribute(Some(file: FFile)) ⇒ attachmentSrv.save(file).map(attachment ⇒ o.updated(path, SetAttribute(attachment)))
                  }
                  .getOrElse(Future.successful(o))
              }
            }
         """
        case (fo, _) ⇒ fo
      }
    q"(attachmentSrv: org.elastic4play.services.AttachmentSrv) ⇒ $saver"
  }

}
