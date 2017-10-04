package org.elastic4play.models

trait FPath {
  val isEmpty: Boolean = false
  def /(elem: FPath): FPath
  def /(elem: String): FPath = this / FPath(elem)
  def toSeq: FPath
  def toSeq(index: Int): FPath
  def toBare: FPath
}

object FPathEmpty extends FPath {
  override val isEmpty: Boolean = true
  def /(elem: FPath): FPath = elem
  override def toString: String = ""
  def toSeq: FPath = sys.error("ERROR: empty.toSeq")
  def toSeq(index: Int): FPath = sys.error(s"ERROR: empty.toSeq($index)")
  def toBare: FPath = this
}
case class FPathElem(head: String, tail: FPath) extends FPath {
  def /(elem: FPath): FPath = elem match {
    case FPathNonameSeq(t) ⇒ FPathSeq(head, t)
    case _                 ⇒ copy(tail = tail / elem)
  }
  override def toString: String = if (tail.isEmpty) s"$head" else s"$head.$tail"
  def toSeq: FPath = if (tail.isEmpty) FPathSeq(head, tail) else copy(tail = tail.toSeq)
  def toSeq(index: Int): FPath = if (tail.isEmpty) FPathElemInSeq(head, index, tail) else copy(tail = tail.toSeq)
  def toBare: FPath = copy(tail = tail.toBare)
}

case class FPathSeq(head: String, tail: FPath) extends FPath {
  def /(elem: FPath): FPath = elem match {
    case _: FPathNonameSeq ⇒ sys.error(s"ERROR: $this / $elem")
    case _                 ⇒ copy(tail = tail / elem)
  }
  override def toString: String = if (tail.isEmpty) s"$head[]" else s"$head[].$tail"
  def toSeq: FPath = if (tail.isEmpty) sys.error(s"ERROR: $this.toSeq") else copy(tail = tail.toSeq)
  def toSeq(index: Int): FPath = if (tail.isEmpty) sys.error(s"ERROR: $this.toSeq($index)") else copy(tail = tail.toSeq)
  def toBare: FPath = copy(tail = tail.toBare)
}

case class FPathElemInSeq(head: String, index: Int, tail: FPath) extends FPath {
  def /(elem: FPath): FPath = elem match {
    case _: FPathNonameSeq ⇒ sys.error(s"ERROR: $this / $elem")
    case _                 ⇒ copy(tail = tail / elem)
  }
  override def toString: String = if (tail.isEmpty) s"$head[$index]" else s"$head[$index].$tail"
  def toSeq: FPath = if (tail.isEmpty) sys.error(s"ERROR: $this.toSeq") else copy(tail = tail.toSeq)
  def toSeq(index: Int): FPath = if (tail.isEmpty) sys.error(s"ERROR: $this.toSeq($index)") else copy(tail = tail.toSeq)
  def toBare: FPath = FPathSeq(head, tail.toBare)
}

case class FPathNonameSeq(tail: FPath) extends FPath {
  def /(elem: FPath): FPath = elem match {
    case _: FPathNonameSeq ⇒ sys.error(s"ERROR: $this / $elem")
    case _                 ⇒ copy(tail = tail / elem)
  }
  override def toString: String = if (tail.isEmpty) s"[]" else s"[].$tail"
  def toSeq: FPath = if (tail.isEmpty) sys.error(s"ERROR: $this.toSeq") else copy(tail = tail.toSeq)
  def toSeq(index: Int): FPath = if (tail.isEmpty) sys.error(s"ERROR: $this.toSeq($index)") else copy(tail = tail.toSeq)
  def toBare: FPath = sys.error(s"$this.toBare")
}
object FPath {
  private val elemInSeqRegex = "(\\w+)\\[(\\d+)\\]".r
  private val seqRegex = "(\\w+)\\[\\]".r
  private val elemRegex = "(\\w+)".r
  def empty: FPath = FPathEmpty
  def apply(path: String): FPath = path.split("\\.").foldRight[FPath](FPathEmpty) {
    case (elemRegex(p), pathElem)             ⇒ FPathElem(p, pathElem)
    case (seqRegex(p), pathElem)              ⇒ FPathSeq(p, pathElem)
    case (elemInSeqRegex(p, index), pathElem) ⇒ FPathElemInSeq(p, index.toInt, pathElem)
    case (other, pathElem)                    ⇒ sys.error(s"ERROR: FPath($other) / $pathElem")
  }
  def seq: FPath = FPathNonameSeq(FPathEmpty)
  //def apply(path: String*): FPath = FPath(path.mkString("."))
  //def seq: FPath = FPath("[]")
  //implicit def fromString(s: String): FPath = FPath(s)
}
//private case class FieldsPathImpl(path: Seq[String]) extends FieldsPath {
//  def /(p: String): FieldsPath = FieldsPathImpl(path :+ p)
//  override def apply(idx: Int): String = path(idx)
//  override def iterator: Iterator[String] = path.iterator
//  override def length: Int = path.length
//  override def toString: String = mkString(".")
//}
