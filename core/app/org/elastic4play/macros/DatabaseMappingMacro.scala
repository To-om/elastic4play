package org.elastic4play.macros

import org.elastic4play.models.{ DatabaseAdapter, WithFieldMapping }

import scala.reflect.macros.blackbox

trait DatabaseMappingMacro extends MacroUtil {
  val c: blackbox.Context

  import c.universe._

  def getDatabaseEntityMapping[E: WeakTypeTag]: Tree = {
    val eType = weakTypeOf[E]
    eType.typeSymbol match {
      case CaseClassType(symbols @ _*) ⇒
        val fieldDefinitionMappings = symbols.map {
          symbol ⇒
            val symbolName = symbol.name.decodedName.toString.trim
            val fieldDefinitionMapping = getDatabaseFieldMapping(symbol, symbol.typeSignature)
            q"$symbolName -> $fieldDefinitionMapping"
        }
        val className = eType.toString.split("\\.").last
        val modelName = Character.toLowerCase(className.charAt(0)) + className.substring(1)
        val mapping = q"org.elastic4play.models.ESEntityMapping(Map(..$fieldDefinitionMappings))"
        q"Map($modelName -> $mapping)"
      case _ ⇒ c.abort(c.enclosingPosition, s"Implicit database mapping of $eType not found")
    }
  }

  private def getDatabaseFieldMapping(symbol: Symbol, eType: Type) = {
    getDatabaseFieldMappingFromAnnotation(symbol, eType)
      .orElse(getDatabaseFieldMappingFromImplicit(eType))
      .orElse(buildDatabaseFieldMapping(eType))
      .getOrElse(c.abort(c.enclosingPosition, s"Fail to get database mapping of $eType"))
  }

  /**
   * Get database mapping from annotation WithFieldMapping
   *
   * @param eType
   * @return Option[Tree[DatabaseAdapter.FieldMappingDefinition[E]\]\]
   */
  private def getDatabaseFieldMappingFromAnnotation(symbol: Symbol, eType: Type): Option[Tree] = {
    val withFieldMappingTpe = appliedType(weakTypeOf[WithFieldMapping[_]].typeConstructor, eType)
    symbol.annotations
      .find(_.tree.tpe =:= withFieldMappingTpe)
      .map { annotation ⇒
        val fieldDefinitionMapping = c.typecheck(annotation.tree.children.tail.head)
        if (fieldDefinitionMapping.tpe <:< weakTypeOf[DatabaseAdapter.FieldMappingDefinition[_]])
          fieldDefinitionMapping
        else
          c.abort(c.enclosingPosition, s"Mapping of $eType is not a FieldDefinition (found $fieldDefinitionMapping: ${fieldDefinitionMapping.tpe})")
      }
  }

  // DatabaseAdapter.FieldMappingDefinition
  private def getDatabaseFieldMappingFromImplicit(eType: Type): Option[Tree] = {
    val mappingType = appliedType(weakTypeOf[DatabaseAdapter.FieldMappingDefinition[_]].typeConstructor, eType)
    val mapping = c.inferImplicitValue(mappingType, silent = true)
    if (mapping.tpe =:= NoType) None
    else Some(mapping)
  }

  private def buildDatabaseFieldMapping(eType: Type): Option[Tree] = {
    eType.typeSymbol match {
      case CaseClassType(symbols @ _*) ⇒
        val fieldDefinitionMappings = symbols.map {
          symbol ⇒
            val symbolName = symbol.name.toString
            val fieldDefinitionMapping = getDatabaseFieldMapping(symbol, symbol.typeSignature)
            q"$symbolName -> $fieldDefinitionMapping"
        }
        Some(q"org.elastic4play.models.ESEntityMapping(Map(..$fieldDefinitionMappings)).toFieldMapping")
      case SeqType(subType)    ⇒ Some(getDatabaseFieldMapping(subType.typeSymbol, subType))
      case OptionType(subType) ⇒ Some(getDatabaseFieldMapping(subType.typeSymbol, subType))
      //case enumeration =>
      case _                   ⇒ None
    }
  }
}
