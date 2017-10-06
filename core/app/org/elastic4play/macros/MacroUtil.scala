package org.elastic4play.macros

import scala.reflect.macros.blackbox

trait MacroUtil {
  val c: blackbox.Context

  import c.universe._

  object CaseClassType {
    def unapplySeq(tpe: Type): Option[Seq[Symbol]] = {
      unapplySeq(tpe.typeSymbol)
    }

    def unapplySeq(s: Symbol): Option[Seq[Symbol]] = {
      if (s.isClass) {
        val c = s.asClass
        if (c.isCaseClass) Some(c.primaryConstructor.typeSignature.paramLists.head)
        else None
      }
      else None
    }
  }

  object SeqType {
    def unapply(s: Symbol): Option[Type] = {
      s match {
        case _: TypeSymbol ⇒ unapply(s.asType.toType)
        case _: TermSymbol ⇒ unapply(s.typeSignature)
      }
    }

    def unapply(tpe: Type): Option[Type] = {
      if (tpe <:< weakTypeOf[Seq[_]]) {
        val TypeRef(_, _, List(subElementType)) = tpe
        Some(subElementType)
      }
      else None
    }
  }

  object OptionType {
    def unapply(s: Symbol): Option[Type] = {
      s match {
        case _: TypeSymbol ⇒ unapply(s.asType.toType)
        case _: TermSymbol ⇒ unapply(s.typeSignature)
      }
    }

    def unapply(tpe: Type): Option[Type] = {
      if (tpe <:< weakTypeOf[Option[_]]) {
        val TypeRef(_, _, List(subElementType)) = tpe
        Some(subElementType)
      }
      else None
    }
  }

  def symbolToType(symbol: Symbol): Type = {
    if (symbol.isType) symbol.asType.toType
    else symbol.typeSignature
  }

  def traverseEntity[E: WeakTypeTag, A](init: A)(f: (Tree, Symbol, A) ⇒ (List[(Tree, Symbol)], A)): A = {

    def unfold(pathSymbolQueue: List[(Tree, Symbol)], currentAcc: A)(f: (Tree, Symbol, A) ⇒ (List[(Tree, Symbol)], A)): A = {
      if (pathSymbolQueue.isEmpty) currentAcc
      else {
        val (listOfPathSymbol, nextAcc) = pathSymbolQueue.foldLeft[(List[(Tree, Symbol)], A)]((Nil, currentAcc)) {
          case ((newPathSymbolQueue, acc), (path, symbol)) ⇒
            val (nextSymbols, a) = f(path, symbol, acc)
            (newPathSymbolQueue ::: nextSymbols) → a
        }
        unfold(listOfPathSymbol, nextAcc)(f)
      }
    }

    unfold(List(q"org.elastic4play.models.FPath.empty" → weakTypeOf[E].typeSymbol), init)(f)
  }
}
