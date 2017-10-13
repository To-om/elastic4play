package org.elastic4play.macros

import scala.reflect.macros.blackbox

import play.api.libs.json.Writes

import org.elastic4play.models.WithOutput

class JsonMacro(val c: blackbox.Context) extends MacroUtil {
  import c.universe._

  def getEntityJsonWrites[E: WeakTypeTag]: Tree = {
    val eType = weakTypeOf[E]
    val writes = _getJsonWrites(eType.typeSymbol, eType)
    val className = eType.toString.split("\\.").last
    val modelName = Character.toLowerCase(className.charAt(0)) + className.substring(1)

    q"""
      import play.api.libs.json.{ JsObject, OWrites, JsString, JsValue, JsNull, JsNumber }
      OWrites[$eType with Entity] { (e: $eType with Entity) ⇒
        $writes.writes(e).as[JsObject] +
          ("_id"        -> JsString(e._id)) +
          ("_routing"   -> JsString(e._routing)) +
          ("_parent"    -> e._parent.fold[JsValue](JsNull)(JsString.apply)) +
          ("_createdAt" -> JsNumber(e._createdAt.getTime())) +
          ("_createdBy" -> JsString(e._createdBy)) +
          ("_updatedAt" -> e._updatedAt.fold[JsValue](JsNull)(d ⇒ JsNumber(d.getTime()))) +
          ("_updatedBy" -> e._updatedBy.fold[JsValue](JsNull)(JsString.apply)) +
          ("_type"      -> JsString($modelName))
      }
      """
  }

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
