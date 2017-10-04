package org.elastic4play.utils

import shapeless.{ HList, Witness }
import scala.language.experimental.macros

import org.elastic4play.macros.RecordMacro

trait Selector[L <: HList, K] {
  type Out
  def apply(l: L): Out
}

object Selector {
  type Aux[L <: HList, K, Out0] = Selector[L, K] { type Out = Out0 }

  def apply[L <: HList, K](implicit selector: Selector[L, K]): Aux[L, K, selector.Out] = selector

  implicit def mkSelector[L <: HList, K, O]: Aux[L, K, O] = macro RecordMacro.mkSelector[L, K]
}

case class Record[C <: HList, T <: HList, E](list: C)(val matFunction: T ⇒ E) {
  type FSL[K] = Selector[C, K]
  def apply(key: Witness)(implicit selector: Selector[C, key.T]): selector.Out = selector(list)
}