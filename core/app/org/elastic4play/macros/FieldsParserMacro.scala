package org.elastic4play.macros

import org.elastic4play.models.{ Attachment, FieldsParser, UpdateFieldsParser, WithParser, WithUpdateParser }
import scala.reflect.macros.blackbox

/**
 * This class build FieldsParser from CreationRecord or DTO
 */
class FieldsParserMacro(val c: blackbox.Context) extends MacroUtil {
  import c.universe._

  def getFieldsParser[E: WeakTypeTag]: Tree = {
    val eType = weakTypeOf[E]
    _getFieldsParser(eType.typeSymbol, eType)
      .getOrElse(c.abort(c.enclosingPosition, s"Build FieldsParser of $eType fails"))
  }

  private def _getFieldsParser(symbol: Symbol, eType: Type): Option[Tree] = {
    if (eType <:< typeOf[Attachment]) {
      Some(q"org.elastic4play.models.FieldsParser.attachment")
    }
    else
      getParserFromAnnotation(symbol, eType)
        .orElse(getParserFromImplicit(eType))
        .orElse(buildParser(eType))
  }

  private def getParserFromAnnotation(symbol: Symbol, eType: Type): Option[Tree] = {
    val withParserType = appliedType(weakTypeOf[WithParser[_]], eType)
    (symbol.annotations ::: eType.typeSymbol.annotations)
      .find(_.tree.tpe <:< withParserType)
      .map(annotation ⇒ annotation.tree.children.tail.head)
  }

  private def getParserFromImplicit(eType: Type): Option[Tree] = {
    val fieldsParserType = appliedType(typeOf[FieldsParser[_]].typeConstructor, eType)
    val fieldsParser = c.inferImplicitValue(fieldsParserType, silent = true)
    if (fieldsParser.tpe =:= NoType) None
    else Some(fieldsParser)
  }

  private def buildParser(eType: Type): Option[Tree] = {
    eType match {
      case CaseClassType(paramSymbols @ _*) ⇒
        val companion = eType.typeSymbol.companion
        val initialBuilder = if (paramSymbols.length > 1) q"($companion.apply _).curried" else q"($companion.apply _)"
        val entityBuilder = paramSymbols
          .foldLeft[Option[Tree]](Some(q"org.scalactic.Good($initialBuilder).orBad[org.scalactic.Every[org.elastic4play.AttributeError]]")) {
            case (maybeBuilder, s) ⇒
              val symbolName = s.name.toString
              for {
                builder ← maybeBuilder
                parser ← _getFieldsParser(s, s.typeSignature)
              } yield q"""
                  import org.scalactic.{ Bad, Every }
                  import org.elastic4play.AttributeError

                  $parser.apply(path / $symbolName, field.get($symbolName)).fold(
                    param => $builder.map(_.apply(param)),
                    error => $builder match {
                      case Bad(errors: Every[_]) => Bad(errors.asInstanceOf[Every[AttributeError]] ++ error)
                      case _ => Bad(error)
                    })
                """
          }

        entityBuilder.map { builder ⇒
          val className: String = eType.toString.split("\\.").last
          q"""
            import org.elastic4play.models.FieldsParser

            FieldsParser[$eType]($className) { case (path, field) => $builder }
          """
        }
      case SeqType(subType) ⇒
        _getFieldsParser(subType.typeSymbol, subType).map { parser ⇒
          q"$parser.sequence"
        }
      case OptionType(subType) ⇒
        _getFieldsParser(subType.typeSymbol, subType).map { parser ⇒
          q"$parser.optional"
        }
      case _ ⇒
        c.abort(c.enclosingPosition, s"Can't build parser for $eType (${eType.typeSymbol.fullName})")
    }
  }

/*************************************************/
  private def getUpdateParserFromAnnotation(symbol: Symbol, eType: Type): Option[Tree] = {
    val withUpdateParserType = appliedType(weakTypeOf[WithUpdateParser[_]], eType)
    (symbol.annotations ::: eType.typeSymbol.annotations)
      .find(_.tree.tpe <:< withUpdateParserType)
      .map(annotation ⇒ annotation.tree.children.tail.head)
  }

  private def getUpdateParserFromImplicit(eType: Type): Option[Tree] = {
    val fieldsParserType = appliedType(typeOf[UpdateFieldsParser[_]].typeConstructor, eType)
    val fieldsParser = c.inferImplicitValue(fieldsParserType, silent = true)
    if (fieldsParser.tpe =:= NoType) None
    else Some(fieldsParser)
  }

  private def buildUpdateParser(eType: Type): Tree = {
    val className: String = eType.toString.split("\\.").last
    val updateFieldsParser = _getFieldsParser(eType.typeSymbol, eType)
      .map { parser ⇒
        q"""
         import org.elastic4play.models.{ FPath, UpdateFieldsParser }
         UpdateFieldsParser[$eType]($className, Map(FPath.empty -> $parser.toUpdate))
        """
      }
      .getOrElse(q"org.elastic4play.models.UpdateFieldsParser.empty[$eType]")

    eType match {
      case SeqType(subType) ⇒
        val subParser = _getUpdateFieldsParser(subType.typeSymbol, subType)
        _getFieldsParser(subType.typeSymbol, subType)
          .map { parser ⇒
            q"""
           import org.elastic4play.models.{ FPath, UpdateFieldsParser }
           UpdateFieldsParser($className, Map(FPath.seq -> $parser.toUpdate))
          """
          }
          .fold(q"$updateFieldsParser ++ $subParser.sequence") { seqParser ⇒
            q"$updateFieldsParser ++ $subParser.sequence ++ $seqParser"
          }
      case OptionType(subType) ⇒
        val parser = _getUpdateFieldsParser(subType.typeSymbol, subType)
        q"$parser ++ $updateFieldsParser"
      case CaseClassType(symbols @ _*) ⇒
        symbols.foldLeft(updateFieldsParser) {
          case (parser, s) ⇒
            val symbolName = s.name.toString
            val subParser = _getUpdateFieldsParser(s, s.typeSignature)
            q"$parser ++ $subParser.on($symbolName)"
        }
      case _ ⇒ updateFieldsParser
    }
  }

  def getUpdateFieldsParser[E: WeakTypeTag]: Tree = {
    val eType = weakTypeOf[E]
    _getUpdateFieldsParser(eType.typeSymbol, eType)
  }

  private def _getUpdateFieldsParser(symbol: Symbol, eType: Type): Tree = {
    getUpdateParserFromAnnotation(symbol, eType)
      .orElse(getUpdateParserFromImplicit(eType))
      .getOrElse(buildUpdateParser(eType))
  }
}
