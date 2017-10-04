package org.elastic4play.services

import javax.inject.{ Inject, Provider, Singleton }

import org.elastic4play.models.Model
import play.api.Logger

import scala.collection.immutable

@Singleton
class ModelSrv @Inject() (modelsProvider: Provider[immutable.Set[Model]]) {
  private[ModelSrv] lazy val logger = Logger(getClass)

  lazy val models = modelsProvider.get
  private[ModelSrv] lazy val modelMap = models.map(m ⇒ m.name → m).toMap
  def apply(modelName: String) = modelMap.get(modelName)
  val list = models
}