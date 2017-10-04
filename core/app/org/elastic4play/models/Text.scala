package org.elastic4play.models

import play.api.libs.json.{ Format, Reads, Writes }

case class Text(value: String) extends AnyVal
object Text {
  val reads = Reads.StringReads.map(s ⇒ Text(s))
  val writes = Writes[Text](text ⇒ Writes.StringWrites.writes(text.value))
  val format = Format(reads, writes)
}
