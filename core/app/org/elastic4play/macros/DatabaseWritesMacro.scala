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

  def _getDatabaseWrites(symbol: Symbol, eType: Type): Tree = {
    getDatabaseWritesFromAnnotation(symbol, eType)
      .orElse(getDatabaseWritesFromImplicit(eType))
      .orElse(buildDatabaseWrites(eType))
      .getOrElse(c.abort(c.enclosingPosition, s"no writes found for $eType"))
  }

  def getDatabaseWritesFromAnnotation(symbol: Symbol, eType: Type): Option[Tree] = {
    val withDatabaseType = appliedType(typeOf[WithDatabase[_]], eType)
    symbol.annotations
      .find(_.tree.tpe <:< withDatabaseType)
      .map(annotation ⇒ annotation.tree.children.tail.tail.head)
  }

  def getDatabaseWritesFromImplicit(eType: Type): Option[Tree] = {
    val databaseWritesType = appliedType(weakTypeOf[DatabaseWrites[_]].typeConstructor, eType)
    val databaseWrites = c.inferImplicitValue(databaseWritesType, silent = true)
    if (databaseWrites.tpe =:= NoType) None
    else Some(databaseWrites)
  }

  def buildDatabaseWrites(eType: Type): Option[Tree] = {
    eType.typeSymbol match {
      case CaseClassType(symbols @ _*) ⇒
        val patterns = symbols.map { symbol ⇒
          val symbolName = symbol.name.toString
          val writes = _getDatabaseWrites(symbol, symbol.typeSignature)
          val pat = TermName(c.freshName(symbolName))
          pat → fq"$pat <- $writes(e.${TermName(symbolName)}).map(_.map($symbolName -> _))"
        }
        Some(q"""
                   (e: $eType) ⇒
                     for(..${patterns.map(_._2)}) yield Some(play.api.libs.json.JsObject(Seq(..${patterns.map(_._1)}).flatten))
                 """)
      case SeqType(subType) ⇒
        val databaseWrites = _getDatabaseWrites(subType.typeSymbol, subType)
        Some(q"""
                import scala.util.{ Try, Success, Failure }
                import play.api.libs.json.{ JsValue, JsArray }

                (es: $eType) ⇒
                  es
                    .map($databaseWrites.apply)
                    .foldLeft[Try[Seq[JsValue]]](Success(Nil)) {
                      case (Success(acc), Success(Some(v))) ⇒ Success(acc :+ v)
                      case (Failure(f), _)                  ⇒ Failure(f)
                      case (_, Failure(f))                  ⇒ Failure(f)
                      case (acc, _)                         ⇒ acc
                    }
                    .map(s ⇒ Some(JsArray(s)))
               """)
      // case Option
    }
  }

  def databaseMaps[E: WeakTypeTag]: Tree = {
    traverseEntity[E, Tree](q"Map.empty[org.elastic4play.models.FPath, org.elastic4play.models.DatabaseWrites[_]]") {
      case (path, symbol, m) ⇒
        val symbolDatabaseWrites = getDatabaseWritesFromAnnotation(symbol, symbol.typeSignature)
          .orElse(getDatabaseWritesFromImplicit(symbol.typeSignature))
          .fold(m) { databaseWrites ⇒ q"$m.updated($path, $databaseWrites)" }

        val nextSymbols = symbol match {
          case CaseClassType(subSymbols @ _*) ⇒
            subSymbols
              .map { s ⇒
                val sName = s.name.toString
                q"$path / $sName" → s
              }
              .toList
          case _ ⇒ Nil
        }

        nextSymbols → symbolDatabaseWrites
    }
  }
}
