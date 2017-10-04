//package org.elastic4play.models
//
//import java.util.Date
//
//import scala.util.Success
//
//import play.api.libs.json._
//import play.api.test.PlaySpecification
//
//import com.sksamuel.elastic4s.ElasticDsl.dateField
//import org.specs2.mock.Mockito
//
//import org.elastic4play.models.ModelSamples.{ certificateOutput, hobbiesDatabaseReads, hobbiesDatabaseWrites, hobbiesParser }
//import org.elastic4play.utils.Hash
//
//case class Person(
//  @ReadOnly name: String,
//  age: Long,
//  hairColor: Option[String],
//  isAdmin: Boolean,
//  @WithFieldMapping(dateField("birthDate").format("epoch_millis")) birthDate: Date,
//  @WithoutOutput password: String,
//  avatar: Attachment,
//  @WithOutput(certificateOutput) certificate: Option[Attachment],
//  @WithParser(hobbiesParser)@WithDatabase(hobbiesDatabaseReads, hobbiesDatabaseWrites) hobbies: Seq[String])
//
//case class ClassAAA(s: String, i: Int)
//case class ClassAAB(l: Long, d: Date)
//case class ClassAA(aaa: Seq[ClassAAA], aab: Option[ClassAAB], s: String)
//case class ClassABA(i: Int)
//case class ClassAB(aba: ClassABA, l: Long)
//case class ClassA(aa: ClassAA, ab: Seq[ClassAB])
//
//object Person extends ModelHolder[Person] {
//  implicit def optionWithNull[T](implicit rds: Reads[T]): Reads[Option[T]] = Reads.optionWithNull
//  val model: Model.Base[Person] = Model[Person]
//}
//
//class ModelSpec extends PlaySpecification with Mockito {
//  "a model" should {
//
//    "have a name" in {
//      Person.model.name must_== "person"
//    }
//
//    "read entry from database" in {
//      val person = Person.model.databaseReads(Some(
//        Json.obj(
//          "_id" → "paul_id",
//          "_routing" → "paul_id",
//          "_createdBy" → "me",
//          "_createdAt" → 1499438917,
//          "name" → "Paul",
//          "age" → 42,
//          "hairColor" → "blond",
//          "isAdmin" → false,
//          "birthDate" → 1499931063,
//          "password" → "secret",
//          "avatar" → Json.obj(
//            "name" → "paul.png",
//            "hashes" → Json.arr("deadbeef"),
//            "size" → 16751,
//            "contentType" → "image/png",
//            "id" → "deadbeef"),
//          "certificate" → Json.obj(
//            "name" → "paul.crt",
//            "hashes" → Json.arr("deadbeef"),
//            "size" → 16751,
//            "contentType" → "application/x-509-server-certificate",
//            "id" → "deadbeef"),
//          "hobbies" → "cinema,swim")))
//
//      val avatar = FAttachment("paul.png", Seq(Hash("deadbeef")), 16751, "image/png", "deadbeef")
//      val certificate = FAttachment("paul.crt", Seq(Hash("deadbeef")), 16751, "application/x-509-server-certificate", "deadbeef")
//      val expectedPerson = new Person("Paul", 42, Some("blond"), false, new Date(1499931063), "secret", avatar, Some(certificate), Seq("cinema", "swim")) with Entity {
//        val _id = "paul_id"
//        val _routing = "paul_id"
//        val _parent = None
//        val _createdBy = "me"
//        val _updatedBy = None
//        val _createdAt = new Date(1499438917)
//        val _updatedAt = None
//        val _model = Person.model
//      }
//
//      person must_=== Success(expectedPerson)
//      //      person._id must_=== "paul_id"
//      //      person._routing must_=== "paul_id"
//      //      person._parent must beNone
//      //      person._createdBy must_=== "me"
//      //      person._updatedBy must beNone
//      //      person._createdAt must_=== new Date(1499438917)
//      //      person._updatedAt must beNone
//      //      person._model must_=== Person.model
//    }
//
//    "write entity in JSON format" in {
//      val avatar = FAttachment("paul.png", Seq(Hash("deadbeef")), 16751, "image/png", "deadbeef")
//      val certificate = FAttachment("paul.crt", Seq(Hash("deadbeef")), 16751, "application/x-509-server-certificate", "deadbeef")
//      val person = new Person("Paul", 42, Some("blond"), false, new Date(1499931063), "secret", avatar, Some(certificate), Seq("cinema", "swim")) with Entity {
//        val _id = "paul_id"
//        val _routing = "paul_id"
//        val _parent = None
//        val _createdBy = "me"
//        val _updatedBy = None
//        val _createdAt = new Date(1499438917)
//        val _updatedAt = None
//        val _model = Person.model
//      }
//      Person.model.writes.writes(person) must_=== Json.obj(
//        "_id" → "paul_id",
//        "_routing" → "paul_id",
//        "_createdBy" → "me",
//        "_createdAt" → 1499438917,
//        "_updatedBy" → JsNull,
//        "_updatedAt" → JsNull,
//        "_parent" → JsNull,
//        "_type" → "person",
//        "name" → "Paul",
//        "age" → 42,
//        "hairColor" → "blond",
//        "password" → "secret",
//        "isAdmin" → false,
//        "birthDate" → 1499931063,
//        "avatar" → Json.obj(
//          "name" → "paul.png",
//          "hashes" → Json.arr("deadbeef"),
//          "size" → 16751,
//          "contentType" → "image/png",
//          "id" → "deadbeef"),
//        "certificate" → "Certificate paul.crt with id deadbeef",
//        "hobbies" → Json.arr("cinema", "swim"))
//    }
//
//    //    "refuse to update a read only attribute" in {
//    //      val fields = Fields(Json.obj("name" → "John"))
//    //      val (parsedFields, remainingFields) = Person.model.updateFieldsParser(fields)
//    //      parsedFields must_=== Bad(One(UpdateReadOnlyAttributeError("name")))
//    //      remainingFields.isEmpty must_== true
//    //    }
//    //
//    //    "refuse to update an attribute if format is not valid" in {
//    //      val fields = Fields(Json.obj("age" → "John"))
//    //      val (parsedFields, remainingFields) = Person.model.updateFieldsParser(fields)
//    //      parsedFields must_=== Bad(One(InvalidFormatAttributeError("age", "long", JsonInputValue(JsString("John")))))
//    //      remainingFields.isEmpty must_== true
//    //    }
//    //
//    //    "refuse to unset mandatory attribute" in {
//    //      val fields = Fields(Json.obj("age" → Json.arr()))
//    //      val (parsedFields, remainingFields) = Person.model.updateFieldsParser(fields)
//    //      parsedFields must_=== Bad(One(MissingAttributeError("age")))
//    //      remainingFields.isEmpty must_== true
//    //    }
//    //
//    //    "be able to override id attribute" in {
//    //      case class Asset(
//    //        @ReadOnly @NotForCreation _id: String,
//    //        `type`: String,
//    //        value: String)
//    //      val assetModel = Model[Asset]
//    //      val inputFields = Fields(Json.obj(
//    //        "type" → "hostname",
//    //        "value" → "myhost.mydomain"))
//    //      val (maybeAsset, remainingFields) = assetModel.createFieldsParser(inputFields)
//    //      remainingFields.isEmpty must_=== true
//    //    }
//
//    "create an entity from record" in {
//      todo
//    }
//  }
//}
