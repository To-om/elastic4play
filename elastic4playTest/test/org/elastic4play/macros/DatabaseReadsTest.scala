package org.elastic4play.macros

import scala.util.{ Failure, Success, Try }

import play.api.libs.json.{ JsString, Json }

import org.specs2.mutable.Specification

import org.elastic4play.models.{ DatabaseReads, DatabaseWrites, WithDatabase }

class DatabaseReadsTest extends Specification with TestUtils {

  "Database reads" should {

    "read simple class from database" in {
      case class SimpleClass(name: String, value: Int)
      val databaseReads = getDatabaseReads[SimpleClass]

      val data = Some(Json.obj(
        "name" -> "SimpleClass",
        "value" -> 53
      ))
      val expectedClass = SimpleClass("SimpleClass", 53)
      databaseReads(data) must_=== Success(expectedClass)
    }

    "read complex class from database" in {
      case class SubClass1(name: String, option: Option[Int])
      case class ComplexClass(name: String, value: Int, subClasses: Seq[SubClass1])
      val databaseReads = getDatabaseReads[ComplexClass]

      val data = Some(Json.obj(
        "name" -> "complexClass",
        "value" -> 42,
        "subClasses" -> Json.arr(
          Json.obj(
            "name" -> "sc1",
          ),
          Json.obj(
            "name" -> "sc2",
            "option" -> 25
          ))))
      val expectedClass = ComplexClass("complexClass", 42, Seq(SubClass1("sc1", None), SubClass1("sc2", Some(25))))
      databaseReads(data) must_=== Success(expectedClass)
    }

    "read class with annotation" in {
      object readWriteDefinitions {
        val subClassRegex = "(\\w+),(\\d+)".r
        val subClass1Reads = DatabaseReads[SubClass] { json =>
          json.fold[Try[SubClass]](Failure(new RuntimeException("Data is missing"))) {
            case JsString(subClassRegex(name, value)) => Success(SubClass(name, value.toInt))
          }
        }
        val subClass1Writes = DatabaseWrites[SubClass] { subClass =>
          Success(Some(JsString(s"${subClass.name},${subClass.value}")))
        }
        val subClass2Reads = DatabaseReads[SubClass] { json =>
          json
            .flatMap { js =>
              for {
                name <- (js \ "nameField").asOpt[String]
                value <- (js \ "valueField").asOpt[Int]
              } yield Success(SubClass(name, value))
            }
            .getOrElse(Failure(new RuntimeException("Data is missing")))
        }
        val subClass2Writes = DatabaseWrites[SubClass] { subClass =>
          Success(Some(Json.obj(
            "nameField" -> subClass.name,
            "valueFields" -> subClass.value
          )))
        }
      }

      @WithDatabase(readWriteDefinitions.subClass1Reads, readWriteDefinitions.subClass1Writes)
      case class SubClass(name: String, value: Int)
      case class ComplexClass(
                               name: String,
                               value: Int,
                               @WithDatabase(readWriteDefinitions.subClass2Reads, readWriteDefinitions.subClass2Writes)
                               sc1: SubClass,
                               sc2: SubClass)

      val databaseReads = getDatabaseReads[ComplexClass]

      val data = Some(Json.obj(
        "name" -> "complexClass",
        "value" -> 42,
        "sc1" -> Json.obj(
          "nameField" -> "sc1",
          "valueField" -> 11
        ),
        "sc2" -> "sc2,25"))
      val expectedClass = ComplexClass("complexClass", 42, SubClass("sc1", 11), SubClass("sc2", 25))

      databaseReads(data) must_=== Success(expectedClass)
    }


    "read class with with implicit" in {
      case class SubClass(name: String, value: Int)
      case class ComplexClass(name: String, value: Int, sc: SubClass)

      val subClassRegex = "(\\w+),(\\d+)".r
      implicit val subClass1Reads = DatabaseReads[SubClass] { json =>
        json.fold[Try[SubClass]](Failure(new RuntimeException("Data is missing"))) {
          case JsString(subClassRegex(name, value)) => Success(SubClass(name, value.toInt))
        }
      }
      implicit val subClass1Writes = DatabaseWrites[SubClass] { subClass =>
        Success(Some(JsString(s"${subClass.name},${subClass.value}")))
      }

      val databaseReads = getDatabaseReads[ComplexClass]

      val data = Some(Json.obj(
        "name" -> "complexClass",
        "value" -> 42,
        "sc" -> "sc1,11"))
      val expectedClass = ComplexClass("complexClass", 42, SubClass("sc1", 11))

      databaseReads(data) must_=== Success(expectedClass)
    }
  }
}
