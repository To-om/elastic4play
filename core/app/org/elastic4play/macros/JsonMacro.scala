package org.elastic4play.macros

import scala.reflect.macros.blackbox

import play.api.libs.json.Writes

import org.elastic4play.models.WithOutput

class JsonMacro(val c: blackbox.Context) extends MacroUtil {
  import c.universe._

  def getJsonWrites[E: WeakTypeTag]: Tree = {
    val eType = weakTypeOf[E]
    _getJsonWrites(eType.typeSymbol, eType)
  }

  private def _getJsonWrites(symbol: Symbol, eType: Type): Tree = {
    getJsonWritesFromAnnotation(symbol, eType)
      .orElse(getJsonWritesFromImplicit(eType))
      .orElse(buildJsonWrites(eType))
      .getOrElse(c.abort(c.enclosingPosition, s"no writes found for $eType"))
  }

  private def getJsonWritesFromAnnotation(symbol: Symbol, eType: Type): Option[Tree] = {
    val withOutputType = appliedType(weakTypeOf[WithOutput[_]], eType)
    (symbol.annotations ::: eType.typeSymbol.annotations)
      .find(_.tree.tpe <:< withOutputType)
      .map(annotation ⇒ annotation.tree.children.tail.head)
  }

  private def getJsonWritesFromImplicit(eType: Type): Option[Tree] = {
    val writesType = appliedType(weakTypeOf[Writes[_]], eType)
    val writes = c.inferImplicitValue(writesType, silent = true)
    if (writes.tpe =:= NoType) None
    else Some(writes)
  }

  private def buildJsonWrites(eType: Type): Option[Tree] = {
    eType match {
      case CaseClassType(symbols @ _*) ⇒
        val params = symbols.map { symbol ⇒
          val symbolName = symbol.name.toString
          val writes = _getJsonWrites(symbol, symbol.typeSignature)
          q"$symbolName -> $writes.writes(e.${TermName(symbolName)})"
        }
        Some(
          q"""
            import play.api.libs.json.{ JsObject, Writes }
            Writes[$eType]((e: $eType) ⇒ JsObject(Seq(..$params)))
          """)
      case SeqType(subType) ⇒
        val writes = _getJsonWrites(subType.typeSymbol, subType)
        Some(q"""
          import play.api.libs.json.{ JsArray, Writes }
          Writes[$eType]((e: $eType) ⇒ JsArray(e.map($writes.writes)))
         """)
      // case Option todo
    }
  }
}
