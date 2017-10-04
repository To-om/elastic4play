//package org.elastic4play.macros
//
//import java.nio.file.Paths
//import java.util.Date
//
//import org.elastic4play.models.{ ClassA, ClassAA, ClassAAA, ClassAAB, ClassAB, ClassABA, FFile, FObject, FString, FUndefined, Field, FPath, Person, UpdateOps }
//import play.api.libs.json.{ JsNumber, Json }
//
//import org.scalactic.{ Bad, Good }
//import org.specs2.mock.Mockito
//import org.specs2.mutable.Specification
//
//import org.elastic4play.{ InvalidFormatAttributeError, MissingAttributeError }
//
//class ModelFieldsParserTest extends Specification with TestUtils with Mockito {
//  "a simple fields parser (create)" should {
//    val personFieldParser = getFieldsParser[Person]
//
//    "return an error if provided fields is not correct" in {
//      val inputFields = FObject(Json.obj(
//        "name" → 12, // invalid format
//        "isAdmin" → true, // unknown attribute
//        "birthDate" → 1499931063,
//        // "password" // missing attribute
//        "internalAttribute" → "void", // unknown attributes
//        "hobbies" → "cinema,swim")) + ("avatar" → FFile("avatar.png", Paths.get("/tmp/avatar.png"), "image/png"))
//      personFieldParser(inputFields) must beLike {
//        case Bad(errors) ⇒ errors.toSeq must contain(exactly[Any](
//          InvalidFormatAttributeError("name", "string", Field(JsNumber(12))),
//          MissingAttributeError("age"),
//          MissingAttributeError("password")))
//      }
//    }
//
//    "create the entity if fields are all correct" in {
//      val avatarFiv = FFile("avatar.png", Paths.get("/tmp/avatar.png"), "image/png")
//      val inputFields = FObject(Json.obj(
//        "name" → "John",
//        "age" → 42,
//        "hairColor" → "blond",
//        "isAdmin" → false,
//        "birthDate" → 1499931063000L,
//        "password" → "secret",
//        "hobbies" → "cinema,swim")) + ("avatar" → avatarFiv)
//      val expectedPerson = Person(
//        name        = "John",
//        age         = 42,
//        hairColor   = Some("blond"),
//        isAdmin     = false,
//        birthDate   = new Date(1499931063000L),
//        password    = "secret",
//        avatar      = avatarFiv,
//        certificate = None,
//        hobbies     = Seq("cinema", "swim"))
//      personFieldParser(inputFields) must_=== Good(expectedPerson)
//    }
//  }
//
//  "a multi-level fields parser" should {
//    val aFieldsParser = getFieldsParser[ClassA]
//    "create a multi-level entity" in {
//      val inputFields = FObject(Json.obj(
//        "aa" → Json.obj(
//          "aaa" → Json.arr(
//            Json.obj(
//              "s" → "a String",
//              "i" → 4),
//            Json.obj(
//              "s" → "another String",
//              "i" → 5)),
//          "s" → "ssss"),
//        "ab" → Json.arr(
//          Json.obj(
//            "aba" → Json.obj(
//              "i" → 42),
//            "l" → 24),
//          Json.obj(
//            "aba" → Json.obj(
//              "i" → 43),
//            "l" → 34))))
//      val expected = ClassA(ClassAA(Seq(ClassAAA("a String", 4), ClassAAA("another String", 5)), None, "ssss"), Seq(ClassAB(ClassABA(42), 24), ClassAB(ClassABA(43), 34)))
//
//      aFieldsParser(inputFields) must_=== Good(expected)
//    }
//
//    "return an error with correct path if sub entity has invalid field" in {
//      val inputFields = FObject(Json.obj(
//        "aa" → Json.obj(
//          "aaa" → Json.arr(
//            Json.obj(
//              "s" → "a String",
//              "i" → 4),
//            Json.obj(
//              "s" → "another String",
//              "i" → 5)),
//          "s" → "ssss"),
//        "ab" → Json.arr(
//          Json.obj(
//            "aba" → Json.obj(
//              "i" → "invalid integer"),
//            "l" → 24),
//          Json.obj(
//            "aba" → Json.obj(
//              "i" → 43),
//            "l" → 34))))
//
//      aFieldsParser(inputFields) must beLike {
//        case Bad(errors) ⇒ errors.toSeq must contain(exactly[Any](
//          InvalidFormatAttributeError("ab[0].aba.i", "int", FString("invalid integer"))))
//      }
//    }
//  }
//  "a fields parser (update)" should {
//    val personFieldParser = getUpdateFieldsParser[Person]
//
//    "create doNothing record from empty fields" in {
//      personFieldParser(FUndefined) must_=== Good(Map.empty)
//    }
//
//    "have parser for each fields" in {
//      personFieldParser.parsers.keys.map(_.toString) must contain(exactly(
//        "",
//        "name",
//        "age",
//        "hairColor",
//        "isAdmin",
//        "birthDate",
//        "password",
//        "avatar",
//        "certificate",
//        "hobbies",
//        "hobbies[]"))
//    }
//
//    "update some fields" in {
//      val inputFields = FObject(Json.obj(
//        "age" → 22,
//        "hobbies" → "golf"))
//      personFieldParser.apply(inputFields) must_=== Good(Map(
//        FPath("age") → UpdateOps.SetAttribute(22),
//        FPath("hobbies") → UpdateOps.SetAttribute(Seq("golf"))))
//    }
//  }
//
//  "a multi-level fields parser (update)" should {
//    val aFieldsParser = getUpdateFieldsParser[ClassA]
//    "update a sublevel field" in {
//      val inputFields = FObject(Json.obj(
//        "aa.aab" → Json.obj(
//          "l" → 12,
//          "d" → 1499931063000L)))
//      aFieldsParser(inputFields) must_=== Good(Map(
//        FPath("aa.aab") → UpdateOps.SetAttribute(ClassAAB(12, new Date(1499931063000L)))))
//    }
//
//    "have parser for each fields" in {
//      aFieldsParser.parsers.keys.map(_.toString) must contain(exactly(
//        "",
//        "aa",
//        "aa.aaa",
//        "aa.aaa[]",
//        "aa.aaa[].s",
//        "aa.aaa[].i",
//        "aa.aab",
//        "aa.aab.l",
//        "aa.aab.d",
//        "aa.s",
//        "ab",
//        "ab[]",
//        "ab[].aba",
//        "ab[].aba.i",
//        "ab[].l"))
//    }
//  }
//}
