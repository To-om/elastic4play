package org.elastic4play

import com.google.inject.AbstractModule
import play.api.{ Configuration, Environment, Logger }

class Module(
  environment: Environment,
  val configuration: Configuration) extends AbstractModule {
  val log = Logger(s"module")

  def configure() = {

  }

}
