package org.elastic4play.macros

import scala.reflect.macros.blackbox

import org.elastic4play.models.{ DatabaseReads, WithDatabase }

trait DatabaseReadsMacro extends MacroUtil {
  val c: blackbox.Context

  import c.universe._

  def getDatabaseReads[E: WeakTypeTag]: Tree = {
    val eType = weakTypeOf[E]
    _getDatabaseReads(eType.typeSymbol, eType)
  }

  private def _getDatabaseReads(symbol: Symbol, eType: Type): Tree = {
    getDatabaseReadsFromAnnotation(symbol, eType)
      .orElse(getDatabaseReadsFromImplicit(eType))
      .orElse(buildDatabaseReads(eType))
      .getOrElse(c.abort(c.enclosingPosition, s"no reads found for $eType"))
  }

  private def getDatabaseReadsFromAnnotation(symbol: Symbol, eType: Type): Option[Tree] = {
    val withDatabaseType = appliedType(typeOf[WithDatabase[_]], eType)
    (symbol.annotations ::: eType.typeSymbol.annotations)
      .find(_.tree.tpe <:< withDatabaseType)
      .map(annotation ⇒ annotation.tree.children.tail.head)
  }

  private def getDatabaseReadsFromImplicit(eType: Type): Option[Tree] = {
    val databaseReadsType = appliedType(typeOf[DatabaseReads[_]], eType)
    val databaseReads = c.inferImplicitValue(databaseReadsType, silent = true)
    if (databaseReads.tpe =:= NoType) None
    else Some(databaseReads)
  }

  private def buildDatabaseReads(eType: Type): Option[Tree] = {
    eType match {
      case CaseClassType(symbols @ _*) ⇒
        val patterns = symbols.map { symbol ⇒
          val symbolName = symbol.name
          val reads = _getDatabaseReads(symbol, symbol.typeSignature)
          val pat = c.freshName(symbolName)
          pat → fq"$pat <- $reads((json \ ${symbolName.toString}).toOption)"

        }
        Some(q"""
          import org.elastic4play.models.DatabaseReads
          import org.elastic4play.InternalError
          import play.api.libs.json.JsValue
          import scala.util.{ Try, Failure }

          DatabaseReads((maybeJson: Option[JsValue]) ⇒ {
            maybeJson.fold[Try[$eType]](Failure(InternalError("Unexpected database format"))) { json ⇒
              for(..${patterns.map(_._2)}) yield new $eType(..${patterns.map(_._1)})
            }
          })
        """)

      case SeqType(subType) ⇒
        val databaseReads = _getDatabaseReads(subType.typeSymbol, subType)
        Some(q"""
                import org.elastic4play.InternalError
                import org.elastic4play.models.DatabaseReads
                import play.api.libs.json.{ JsArray, JsValue }
                import scala.util.{ Failure, Success, Try }

                DatabaseReads((json: Option[JsValue]) ⇒ {
                  json.fold[Try[Seq[$subType]]](Success(Nil)) {
                    case JsArray(values) ⇒
                      values
                        .map(v ⇒ $databaseReads(Some(v)))
                        .foldLeft[Try[Seq[$subType]]](Success(Nil)) {
                          case (Success(acc), Success(elm)) ⇒ Success(acc :+ elm)
                          case (Success(acc), Failure(f))   ⇒ Failure(f)
                          case (failure, _)                 ⇒ failure
                        }
                    case other ⇒ Failure(InternalError("Unexpected value in database"))
                  }
                })
               """)

      case OptionType(subType) ⇒
        val databaseReads = _getDatabaseReads(subType.typeSymbol, subType)
        Some(q"""
                import org.elastic4play.models.DatabaseReads
                import play.api.libs.json.{ JsValue, JsNull }
                import scala.util.{ Success, Try }

                DatabaseReads((json: Option[JsValue]) ⇒ {
                  json.fold[Try[Option[$subType]]](Success(None)) {
                    case JsNull ⇒ Success(None)
                    case other  ⇒ $databaseReads(json).map(v ⇒ Some(v))
                  }
                })
               """)
      case _ ⇒
        println(s"WARNING: $eType is not a case class nor a seq nor an option !!")
        None
    }
  }
}
