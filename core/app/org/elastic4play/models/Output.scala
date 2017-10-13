package org.elastic4play.models

import scala.language.experimental.macros

import play.api.libs.json.Writes

import org.elastic4play.macros.JsonMacro

object Output {
  def apply[T]: Writes[T] = macro JsonMacro.getJsonWrites[T]
}