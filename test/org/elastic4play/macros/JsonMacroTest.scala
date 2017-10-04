package org.elastic4play.macros

import java.util.Date

import play.api.libs.json._
import org.specs2.mutable.Specification
import org.elastic4play.models.{ Entity, Model, WithOutput }
import org.specs2.mock.Mockito

import scala.util.Success

class JsonMacroTest extends Specification with TestUtils with Mockito {
  "Json macro" should {
    "write simple entity" in {
      case class SimpleClass(name: String, value: Int)
      val writes = getEntityJsonWrites[SimpleClass]

      object simpleClassEntity extends SimpleClass("simple_class", 42) with Entity {
        val _id: String = "simple_class_id"
        val _routing: String = "simple_class_routing"
        val _parent: Option[String] = None
        val _model: Model = null
        val _createdBy: String = "me"
        val _updatedBy: Option[String] = None
        val _createdAt: Date = new Date(1507044072000L)
        val _updatedAt: Option[Date] = None
      }

      val expectedOutput = Json.obj(
        "name" -> "simple_class",
        "value" -> 42,
        "_type" -> "simpleClass",
        "_id" -> "simple_class_id",
        "_routing" -> "simple_class_routing",
        "_parent" -> JsNull,
        "_createdBy" -> "me",
        "_createdAt" -> 1507044072000L,
        "_updatedBy" -> JsNull,
        "_updatedAt" -> JsNull)

      writes.writes(simpleClassEntity).fields must containTheSameElementsAs(expectedOutput.fields)
    }

    "write entity with option, sequence and sub-class attributes" in {
      case class SubClass1(name: String, option: Option[Int])
      case class ComplexClass(name: String, value: Int, subClasses: Seq[SubClass1])
      val writes = getEntityJsonWrites[ComplexClass]

      object complexClassEntity extends ComplexClass("complex_class", 42, Seq(SubClass1("sc1", Some(12)), SubClass1("sc2", None))) with Entity {
        val _id: String = "complex_class_id"
        val _routing: String = "complex_class_routing"
        val _parent: Option[String] = None
        val _model: Model = null
        val _createdBy: String = "me"
        val _updatedBy: Option[String] = None
        val _createdAt: Date = new Date(1507044072000L)
        val _updatedAt: Option[Date] = None
      }
      
      val expectedOutput = Json.obj(
        "name" -> "complex_class",
        "value" -> 42,
        "subClasses" -> Json.arr(
          Json.obj("name" -> "sc1", "option" -> 12),
          Json.obj("name" -> "sc2", "option" -> JsNull),
        ),
        "_type" -> "complexClass",
        "_id" -> "complex_class_id",
        "_routing" -> "complex_class_routing",
        "_parent" -> JsNull,
        "_createdBy" -> "me",
        "_createdAt" -> 1507044072000L,
        "_updatedBy" -> JsNull,
        "_updatedAt" -> JsNull)

      writes.writes(complexClassEntity).fields must containTheSameElementsAs(expectedOutput.fields)
    }

    "write simple entity with annotation" in {
      val nameWrites = Writes[String] { name =>
        JsArray(name.toSeq.map(c => JsString(c.toString)))
      }
      val subClassWrites1 = Writes[SubClass] { subClass =>
        JsString(s"SubClass1(${subClass.name})")
      }
      val subClassWrites2 = Writes[SubClass] { subClass =>
        JsString(s"SubClass2(${subClass.name})")
      }
      @WithOutput(subClassWrites2)
      case class SubClass(name: String)
      case class SimpleClass(
                              @WithOutput(nameWrites)
                              name: String,
                              value: Int,
                              @WithOutput(subClassWrites1)
                              sc1: SubClass,
                              sc2: SubClass)
      val writes = getEntityJsonWrites[SimpleClass]

      object simpleClassEntity extends SimpleClass("simple_class", 42, SubClass("sc1"), SubClass("sc2")) with Entity {
        val _id: String = "simple_class_id"
        val _routing: String = "simple_class_routing"
        val _parent: Option[String] = None
        val _model: Model = null
        val _createdBy: String = "me"
        val _updatedBy: Option[String] = None
        val _createdAt: Date = new Date(1507044072000L)
        val _updatedAt: Option[Date] = None
      }
      val expectedOutput = Json.obj(
        "name" -> Json.arr("s", "i", "m", "p", "l", "e", "_", "c", "l", "a", "s", "s"),
        "value" -> 42,
        "sc1" -> "SubClass1(sc1)",
        "sc2" -> "SubClass2(sc2)",
        "_type" -> "simpleClass",
        "_id" -> "simple_class_id",
        "_routing" -> "simple_class_routing",
        "_parent" -> JsNull,
        "_createdBy" -> "me",
        "_createdAt" -> 1507044072000L,
        "_updatedBy" -> JsNull,
        "_updatedAt" -> JsNull)

      writes.writes(simpleClassEntity).fields must containTheSameElementsAs(expectedOutput.fields)
    }

    "write simple entity with implicit" in {
      implicit val subClassWrites: Writes[SubClass] = Writes[SubClass] { subClass =>
        JsString(s"subClass(${subClass.name})")
      }
      case class SubClass(name: String)
      case class SimpleClass(name: String, value: Int, subClass: SubClass)
      val writes = getEntityJsonWrites[SimpleClass]

      object simpleClassEntity extends SimpleClass("simple_class", 42, SubClass("value")) with Entity {
        val _id: String = "simple_class_id"
        val _routing: String = "simple_class_routing"
        val _parent: Option[String] = None
        val _model: Model = null
        val _createdBy: String = "me"
        val _updatedBy: Option[String] = None
        val _createdAt: Date = new Date(1507044072000L)
        val _updatedAt: Option[Date] = None
      }
      val expectedOutput = Json.obj(
        "name" -> "simple_class",
        "value" -> 42,
        "subClass" -> "subClass(value)",
        "_type" -> "simpleClass",
        "_id" -> "simple_class_id",
        "_routing" -> "simple_class_routing",
        "_parent" -> JsNull,
        "_createdBy" -> "me",
        "_createdAt" -> 1507044072000L,
        "_updatedBy" -> JsNull,
        "_updatedAt" -> JsNull)

      writes.writes(simpleClassEntity).fields must containTheSameElementsAs(expectedOutput.fields)
    }

    "build a entity from an object, a json and a model" in {
      case class SimpleClass(name: String, value: Int)
      val entityReader = mkEntityReader[SimpleClass]

      val simpleClass = SimpleClass("simple_class", 42)
      object simpleClassEntity extends SimpleClass("simple_class", 42) with Entity {
        val _id: String = "simple_class_id"
        val _routing: String = "simple_class_routing"
        val _parent: Option[String] = None
        val _model: Model = null
        val _createdBy: String = "me"
        val _updatedBy: Option[String] = None
        val _createdAt: Date = new Date(1507044072000L)
        val _updatedAt: Option[Date] = None
      }
      val entityJson = Json.obj(
        "_type" -> "simpleClass",
        "_id" -> "simple_class_id",
        "_routing" -> "simple_class_routing",
        "_parent" -> JsNull,
        "_createdBy" -> "me",
        "_createdAt" -> 1507044072000L,
        "_updatedBy" -> JsNull,
        "_updatedAt" -> JsNull)

      entityReader(entityJson, mock[Model], Success(simpleClass)) must_=== Success(simpleClassEntity)
    }
  }
}
