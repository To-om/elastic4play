package org.elastic4play.macros

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import org.elastic4play.models.{ TransformInto, EntityModel }

class AnnotationMacroTest extends Specification with TestUtils with Mockito {

  case class MyEntity(u: String, e: String, d: Double)

  "transformation macro" should {
    "be activated on class with annotation" in {

      @TransformInto[MyEntity]
      case class MyDTO(s: String, i: Int, u: String) {
        def _e = s
        def _d = i.toDouble
      }

      MyDTO("sParam", 42, "uParam").toMyEntity must_=== MyEntity("uParam", "sParam", 42.0)
    }

    "create model companion" in {
      @EntityModel
      case class MyEntity(name: String, value: Int)

      MyEntity.model.name must_=== "myEntity"
    }
  }
}