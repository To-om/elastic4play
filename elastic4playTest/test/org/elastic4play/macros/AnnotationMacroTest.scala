package org.elastic4play.macros

import java.util.Date

import play.api.libs.json.{ JsNull, JsObject, Json }

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import org.elastic4play.models.{ JsonOutput, _ }

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

    "output case class" in {
      @JsonOutput
      case class MyDTO(s: String, i: Int, u: String, v: Boolean)

      val expectedOutput = Json.obj(
        "s" -> "sParam",
        "i" -> 42,
        "u" -> "uParam",
        "v" -> true)
      MyDTO("sParam", 42, "uParam", true).toJson.fields must containTheSameElementsAs(expectedOutput.fields)
    }

    "output entity" in {
      @JsonOutput
      case class MyOtherEntity(u: String, e: String, d: Double, v: Boolean)
      val myEntity = new MyOtherEntity("uParam", "eParam", 42.1, true) with Entity {
        val _id = "entityId"
        val _routing = "routingInformation"
        val _parent = None
        val _model: Model = null
        val _createdBy = "me"
        val _updatedBy = None
        val _createdAt = new Date(1507878244000L)
        val _updatedAt = None
      }
      val expectedOutput = Json.obj(
        "u" -> "uParam",
        "e" -> "eParam",
        "d" -> 42.1,
        "v" -> true,
        "_id" -> "entityId",
        "_routing" -> "routingInformation",
        "_parent" -> JsNull,
        "_createdAt" -> 1507878244000L,
        "_createdBy" -> "me",
        "_updatedAt" -> JsNull,
        "_updatedBy" -> JsNull,
        "_type" -> "MyOtherEntity")
      myEntity.toJson(myEntity).fields must containTheSameElementsAs(expectedOutput.fields)
    }
  }
}