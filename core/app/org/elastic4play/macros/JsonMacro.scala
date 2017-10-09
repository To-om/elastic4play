package org.elastic4play.macros

import scala.reflect.macros.blackbox

import play.api.libs.json.Writes

import org.elastic4play.models.WithOutput

trait JsonMacro extends MacroUtil {
  val c: blackbox.Context

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
