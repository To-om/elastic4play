package org.elastic4play.macros

import scala.reflect.macros.blackbox

import org.elastic4play.models.{ DatabaseWrites, WithDatabase }

trait DatabaseWritesMacro extends MacroUtil {
  val c: blackbox.Context

  import c.universe._

  def getDatabaseWrites[E: WeakTypeTag]: Tree = {
    val eType = weakTypeOf[E]
    _getDatabaseWrites(eType.typeSymbol, eType)
  }

  private def _getDatabaseWrites(symbol: Symbol, eType: Type): Tree = {
    getDatabaseWritesFromAnnotation(symbol, eType)
      .orElse(getDatabaseWritesFromImplicit(eType))
      .orElse(buildDatabaseWrites(eType))
      .getOrElse(c.abort(c.enclosingPosition, s"no writes found for $eType"))
  }

  private def getDatabaseWritesFromAnnotation(symbol: Symbol, eType: Type): Option[Tree] = {
    val withDatabaseType = appliedType(typeOf[WithDatabase[_]], eType)
    (symbol.annotations ::: eType.typeSymbol.annotations)
      .find(_.tree.tpe <:< withDatabaseType)
      .map(annotation ⇒ annotation.tree.children.tail.tail.head)
  }

  private def getDatabaseWritesFromImplicit(eType: Type): Option[Tree] = {
    val databaseWritesType = appliedType(weakTypeOf[DatabaseWrites[_]].typeConstructor, eType)
    val databaseWrites = c.inferImplicitValue(databaseWritesType, silent = true)
    if (databaseWrites.tpe =:= NoType) None
    else Some(databaseWrites)
  }

  private def buildDatabaseWrites(eType: Type): Option[Tree] = {
    eType match {
      case CaseClassType(symbols @ _*) ⇒
        val patterns = symbols.map { symbol ⇒
          val symbolName = symbol.name.toString
          val writes = _getDatabaseWrites(symbol, symbol.typeSignature)
          val pat = TermName(c.freshName(symbolName))
          pat → fq"$pat <- $writes(e.${TermName(symbolName)}).map(_.map($symbolName -> _))"
        }
        Some(q"""
                 import scala.util.Try
                 import org.elastic4play.models.{ DatabaseWrites, DatabaseAdapter }

                 new DatabaseWrites[$eType] {
                   def apply(e: $eType): Try[DatabaseAdapter.DatabaseFormat] =
                     for(..${patterns.map(_._2)}) yield Some(play.api.libs.json.JsObject(Seq(..${patterns.map(_._1)}).flatten))
                 }
                 """)
      case SeqType(subType) ⇒
        val databaseWrites = _getDatabaseWrites(subType.typeSymbol, subType)
        Some(q"$databaseWrites.sequence")
      case OptionType(subType) ⇒
        val databaseWrites = _getDatabaseWrites(subType.typeSymbol, subType)
        Some(q"$databaseWrites.optional")
      case _ ⇒ None
    }
  }

  def databaseMaps[E: WeakTypeTag]: Tree = {
    def nextSymbol(path: Tree, tpe: Type): List[(Tree, Symbol)] = {
      tpe match {
        case CaseClassType(subSymbols @ _*) ⇒
          subSymbols
            .map { s ⇒
              val sName = s.name.toString
              q"$path / $sName" → s
            }
            .toList
        case SeqType(subType)    ⇒ (q"$path.toSeq", subType.typeSymbol) :: nextSymbol(q"$path.toSeq", subType)
        case OptionType(subType) ⇒ nextSymbol(path, subType)
        case _                   ⇒ Nil
      }
    }

    val databaseWritesList = traverseEntity[E, Seq[Tree]](Nil) {
      case (path, symbol, m) ⇒
        val symbolType = symbolToType(symbol)
        val symbolDatabaseWrites = getDatabaseWritesFromAnnotation(symbol, symbolType)
          .orElse(getDatabaseWritesFromImplicit(symbolType))
          .orElse(buildDatabaseWrites(symbolType))
          .fold(m) { databaseWrites ⇒ m :+ q"$path -> $databaseWrites" }

        val ns = nextSymbol(path, symbolType)
        ns → symbolDatabaseWrites
    }
    q"Seq(..$databaseWritesList).toMap - org.elastic4play.models.FPath.empty"
  }
}
