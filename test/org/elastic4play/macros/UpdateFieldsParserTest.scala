//package org.elastic4play.macros
//
//import play.api.libs.json.Json
//
//import org.scalactic.{ Bad, Good }
//import org.specs2.mock.Mockito
//import org.specs2.mutable.Specification
//
//import org.elastic4play.InvalidFormatAttributeError
//import org.elastic4play.models.{ FNumber, FPath, Field, UpdateOps }
//
//class UpdateFieldsParserTest extends Specification with TestUtils with Mockito {
//
//  case class Actor(id: Int, role: String, username: String)
//
//  case class HardwareDetails(cpu: String, ram: Int, disk: Int)
//
//  case class Host(
//    name: String,
//    address: String,
//    aliases: Seq[String],
//    actors: Seq[Actor],
//    hardwareDetails: HardwareDetails)
//
//  "a fields parser (update)" should {
//    val hostFieldParser = getUpdateFieldsParser[Host]
//
//    "return an error if provided fields is not correct" in {
//      val inputFields = Field(Json.obj(
//        "address" → 12)) // invalid format
//
//      hostFieldParser(inputFields) must beLike {
//        case Bad(errors) ⇒ errors.toSeq must contain(exactly[Any](
//          InvalidFormatAttributeError("address", "string", FNumber(12))))
//      }
//    }
//
//    "update a simple attribute" in {
//      val inputFields = Field(Json.obj(
//        "address" → "127.0.0.1"))
//
//      hostFieldParser(inputFields) must_=== Good(Map(
//        FPath("address") → UpdateOps.SetAttribute("127.0.0.1")))
//    }
//
//    "update a simple attribute in a sequence" in {
//      val inputFields = Field(Json.obj(
//        "aliases[]" → "localhost",
//        "aliases[2]" → "here"))
//
//      hostFieldParser(inputFields) must_=== Good(Map(
//        FPath("aliases[]") → UpdateOps.SetAttribute("localhost"),
//        FPath("aliases[2]") → UpdateOps.SetAttribute("here")))
//    }
//    //
//    //    "update a sequence of simple attribute" in {
//    //      val inputFields = Fields(Json.obj(
//    //        "aliases" → Json.arr("localhost", "local", "localhost.localdomain")))
//    //
//    //      val (maybeHost, remainingFields) = hostFieldParser(inputFields)
//    //
//    //      maybeHost must beLike {
//    //        case Good(r) ⇒
//    //          r('aliases) must_=== UpdateOps.SetAttribute(Seq("localhost", "local", "localhost.localdomain"))
//    //      }
//    //
//    //      remainingFields must beEmpty
//    //    }
//    //
//    //    "update a composite attribute" in {
//    //      val inputFields = Fields(Json.obj(
//    //        "hardwareDetails" → Json.obj(
//    //          "cpu" → "i7",
//    //          "ram" → 16,
//    //          "disk" → 1000)))
//    //
//    //      val (maybeHost, remainingFields) = hostFieldParser(inputFields)
//    //
//    //      maybeHost must beLike {
//    //        case Good(r) ⇒
//    //          r('hardwareDetails) must_=== UpdateOps.SetAttribute(HardwareDetails("i7", 16, 1000))
//    //      }
//    //
//    //      remainingFields must beEmpty
//    //    }
//    //
//    //    "update a composite attribute in a sequence" in {
//    //      todo
//    //    }
//    //
//    //    "update a sequence of composite attribute" in {
//    //      todo
//    //    }
//  }
//}
