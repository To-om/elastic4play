package org.elastic4play.macros

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import org.elastic4play.models.{ EntityModel, TransformFrom, TransformInto }

class AnnotationMacroTest extends Specification with TestUtils with Mockito {

  case class MyEntity(u: String, e: String, d: Double, v: Boolean)

  "transformation macro" should {
    "create toEntity method" in {

      @TransformInto[MyEntity]
      case class MyDTO(s: String, i: Int, u: String, v: Boolean) {
        def _e = s
        def _d = i.toDouble
        def _u = s"__${u}__"
      }

      MyDTO("sParam", 42, "uParam", true).toMyEntity must_=== MyEntity("__uParam__", "sParam", 42.0, true)
    }

    "create fromEntity method" in {
      @TransformFrom[MyEntity]
      case class MyDTO(s: String, i: Int, u: String, v: Boolean)
      object MyDTO {
        def _s(myEntity: MyEntity) = myEntity.e
        def _i(myEntity: MyEntity) = myEntity.d.toInt
        val uRegex = "__(.+)__".r
        def _u(myEntity: MyEntity) = myEntity.u match {
          case uRegex(u) ⇒ u
          case u         ⇒ u
        }
      }

      MyDTO.fromMyEntity(MyEntity("__uParam__", "sParam", 42.0, true)) must_=== MyDTO("sParam", 42, "uParam", true)
    }

    "create model companion" in {
      @EntityModel
      case class MyEntity(name: String, value: Int)

      MyEntity.model.name must_=== "myEntity"
    }
  }
}