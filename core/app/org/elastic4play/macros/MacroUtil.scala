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

  def traverseEntity[E: WeakTypeTag, A](acc: A)(f: (Tree, Symbol, A) ⇒ (List[(Tree, Symbol)], A)): A = {

    def unfold(unproccessedSymbols: List[(Tree, Symbol)], intermediateAcc: A)(f: (Tree, Symbol, A) ⇒ (List[(Tree, Symbol)], A)): A = {
      if (unproccessedSymbols.isEmpty) intermediateAcc
      else {
        val (listOfPathSymbol, _a) = unproccessedSymbols.foldLeft[(List[(Tree, Symbol)], A)]((Nil, intermediateAcc)) {
          case ((ps, a), (path, symbol)) ⇒
            val (nextSymbols, r) = f(path, symbol, a)
            (ps ::: nextSymbols) → r
        }
        unfold(listOfPathSymbol, _a)(f)
      }
    }

    unfold(List(q"org.elastic4play.models.FPath.empty" → weakTypeOf[E].typeSymbol), acc)(f)
  }
}
