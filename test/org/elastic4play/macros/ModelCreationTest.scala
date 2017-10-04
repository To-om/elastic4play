//package org.elastic4play.macros
//
//import java.nio.file.Paths
//import java.util.Date
//
//import org.elastic4play.models.{ DatabaseReads, DatabaseWrites, Entity, FAttachment, FFile, Metrics, Person }
//import org.elastic4play.services.AttachmentSrv
//import org.elastic4play.utils.Hash
//import org.specs2.concurrent.ExecutionEnv
//import org.specs2.mock.Mockito
//import org.specs2.mutable.Specification
//import play.api.libs.json._
//import scala.concurrent.Future
//import scala.concurrent.duration._
//import scala.util.{ Failure, Success }
//
//class ModelCreationTest extends Specification with TestUtils with Mockito {
//  "Model macro" should {
//    //    "build database attributes" in {
//    //      val personDatabaseAttributes = mkDatabaseAttributes[Person]
//    //      personDatabaseAttributes.map(_.name) must_=== Seq(
//    //        "name", "age", "hairColor", "isAdmin", "birthDate", "password", "internalAttribute", "avatar", "certificate", "hobbies")
//    //    }
//
//    "build database JSON writer" in {
//      val writes = getDatabaseWrites[Person]
//      val avatar = FAttachment("paul.png", Seq(Hash("deadbeef")), 16751, "image/png", "deadbeef")
//      val certificate = FAttachment("paul.crt", Seq(Hash("deadbeef")), 16751, "application/x-509-server-certificate", "deadbeef")
//
//      writes(Person(
//        name        = "Paul",
//        age         = 42,
//        hairColor   = Some("blond"),
//        isAdmin     = false,
//        birthDate   = new Date(1499931063),
//        password    = "secret",
//        avatar      = avatar,
//        certificate = Some(certificate),
//        hobbies     = Seq("cinema", "swim"))) must_===
//        Success(Some(Json.obj(
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
//    }
//
//    //    "build database JSON reader" in {
//    //      val reads = mkDatabaseReadsEntity[Person](mock[Model])
//    //
//    //      val personJson = Json.obj(
//    //        "_id" → "paul_id",
//    //        "_routing" → "paul_id",
//    //        "_createdBy" → "me",
//    //        "_createdAt" → 1499438917,
//    //        "name" → "Paul",
//    //        "age" → 42,
//    //        "hairColor" → "blond",
//    //        "isAdmin" → false,
//    //        "birthDate" → 1499931063,
//    //        "password" → "secret",
//    //        "avatar" → Json.obj(
//    //          "name" → "paul.png",
//    //          "hashes" → Json.arr("deadbeef"),
//    //          "size" → 16751,
//    //          "contentType" → "image/png",
//    //          "id" → "deadbeef"),
//    //        "certificate" → Json.obj(
//    //          "name" → "paul.crt",
//    //          "hashes" → Json.arr("deadbeef"),
//    //          "size" → 16751,
//    //          "contentType" → "application/x-509-server-certificate",
//    //          "id" → "deadbeef"),
//    //        "hobbies" → "cinema,swim")
//    //
//    //      val avatar = FAttachment("paul.png", Seq(Hash("deadbeef")), 16751, "image/png", "deadbeef")
//    //      val certificate = FAttachment("paul.crt", Seq(Hash("deadbeef")), 16751, "application/x-509-server-certificate", "deadbeef")
//    //      val expectedPerson = new Person("Paul", 42, Some("blond"), false, new Date(1499931063), "secret", avatar, Some(certificate), Seq("cinema", "swim")) with Entity {
//    //        val _id = "paul_id"
//    //        val _routing = "paul_id"
//    //        val _parent = None
//    //        val _createdBy = "me"
//    //        val _updatedBy = None
//    //        val _createdAt = new Date(1499438917)
//    //        val _updatedAt = None
//    //        val _model = null
//    //      }
//    //
//    //      reads(Some(personJson)) match {
//    //        case Success(person) ⇒
//    //          person must_=== expectedPerson
//    //          person._id must_=== "paul_id"
//    //          person._routing must_=== "paul_id"
//    //          person._parent must beNone
//    //          person._createdBy must_=== "me"
//    //          person._updatedBy must beNone
//    //          person._createdAt must_=== new Date(1499438917)
//    //          person._updatedAt must beNone
//    //        //person._model must_=== ???
//    //        case Failure(e) ⇒ ko(e.toString)
//    //      }
//    //    }
//
//    "build output JSON writer" in {
//      val writes = getEntityJsonWrites[Person]
//      val avatar = FAttachment("paul.png", Seq(Hash("deadbeef")), 16751, "image/png", "deadbeef")
//      val certificate = FAttachment("paul.crt", Seq(Hash("deadbeef")), 16751, "application/x-509-server-certificate", "deadbeef")
//      val personEntity = new Person("Paul", 42, Some("blond"), false, new Date(1499931063), "secret", avatar, Some(certificate), Seq("cinema", "swim")) with Entity {
//        val _id = "paul_id"
//        val _routing = "paul_id"
//        val _parent = None
//        val _createdBy = "me"
//        val _updatedBy = None
//        val _createdAt = new Date(1499438917)
//        val _updatedAt = None
//        val _model = null
//      }
//      writes.writes(personEntity).fields must containTheSameElementsAs(Json.obj(
//        "_id" → "paul_id",
//        "_routing" → "paul_id",
//        "_parent" → JsNull,
//        "_createdBy" → "me",
//        "_createdAt" → 1499438917,
//        "_updatedBy" → JsNull,
//        "_updatedAt" → JsNull,
//        "_type" → "person",
//        "name" → "Paul",
//        "age" → 42,
//        "hairColor" → "blond",
//        "isAdmin" → false,
//        "birthDate" → 1499931063,
//        "password" → "secret",
//        "avatar" → Json.obj(
//          "name" → "paul.png",
//          "hashes" → Json.arr("deadbeef"),
//          "size" → 16751,
//          "contentType" → "image/png",
//          "id" → "deadbeef"),
//        "certificate" → "Certificate paul.crt with id deadbeef",
//        "hobbies" → Json.arr("cinema", "swim")).fields)
//    }
//
//    "build an attachment saver" in {
//      implicit val ee = ExecutionEnv.fromGlobalExecutionContext
//      val attachmentSaver = mkAttachSaver[Person]
//
//      val avatarFIV = FFile("paul.png", Paths.get("/tmp/xx.tmp"), "image/png")
//      val avatarAIV = FAttachment("paul.png", Seq(Hash("deadbeef")), 16751, "image/png", "deadbeef")
//
//      val certificateFIV = FFile("paul.crt", Paths.get("/tmp/xx.tmp"), "application/x-509-server-certificate")
//      val certificateAIV = FAttachment("paul.crt", Seq(Hash("deadbeef")), 16751, "application/x-509-server-certificate", "deadbeef")
//
//      val attachmentSrv = mock[AttachmentSrv]
//      attachmentSrv.save(avatarFIV) returns Future.successful(avatarAIV)
//      attachmentSrv.save(certificateFIV) returns Future.successful(certificateAIV)
//
//      val personWithFIV = Person("Paul", 42, Some("blond"), isAdmin = false, new Date(1499931063), "secret", avatarFIV, Some(certificateFIV), Seq("cinema", "swim"))
//      val personWithAIV = Person("Paul", 42, Some("blond"), isAdmin = false, new Date(1499931063), "secret", avatarAIV, Some(certificateAIV), Seq("cinema", "swim"))
//
//      attachmentSaver(attachmentSrv)(personWithFIV) must beEqualTo(personWithAIV).awaitFor(1.second)
//    }
//  }
//
//  //  "Model macro with id attribute" should {
//  //    case class PersonWithId(
//  //      @ReadOnly @NotForCreation _id: String,
//  //      name: String,
//  //      age: Int)
//  //    //    "build database attributes" in {
//  //    //      val personDatabaseAttributes = mkDatabaseAttributes[PersonWithId]
//  //    //      personDatabaseAttributes.map(_.name) must_=== Seq(
//  //    //        "_id", "name", "age")
//  //    //    }
//  //
//  //    "build database JSON writer" in {
//  //      val writes = getDatabaseWrites[PersonWithId]
//  //      writes(PersonWithId("Paul42", "Paul", 42)) must_===
//  //        Success(Some(Json.obj(
//  //          "_id" → "Paul42",
//  //          "name" → "Paul",
//  //          "age" → 42)))
//  //    }
//  //
//  //    "build database JSON reader" in {
//  //      val reads = mkDatabaseReadsEntity[PersonWithId](mock[Model])
//  //      val personJson = Json.obj(
//  //        "_id" → "Paul42",
//  //        "_routing" → "paul_routing",
//  //        "_createdBy" → "me",
//  //        "_createdAt" → 1499438917,
//  //        "name" → "Paul",
//  //        "age" → 42)
//  //
//  //      val expectedPerson = new PersonWithId("Paul42", "Paul", 42) with Entity {
//  //        //val _id = "paul_id"
//  //        val _routing = "paul_routing"
//  //        val _parent = None
//  //        val _createdBy = "me"
//  //        val _updatedBy = None
//  //        val _createdAt = new Date(1499438917)
//  //        val _updatedAt = None
//  //        val _model = null
//  //      }
//  //
//  //      reads(Some(personJson)) match {
//  //        case Success(person) ⇒
//  //          person must_=== expectedPerson
//  //          person._id must_=== "Paul42"
//  //          person._routing must_=== "paul_routing"
//  //          person._parent must beNone
//  //          person._createdBy must_=== "me"
//  //          person._updatedBy must beNone
//  //          person._createdAt must_=== new Date(1499438917)
//  //          person._updatedAt must beNone
//  //        //person._model must_=== ???
//  //        case Failure(e) ⇒ ko(e.toString)
//  //      }
//  //
//  //    }
//  //
//  //    "build output JSON writer" in {
//  //      val writes = mkOutputWrites[PersonWithId]
//  //      val personEntity = new PersonWithId("Paul42", "Paul", 42) with Entity {
//  //        //val _id = "paul_id"
//  //        val _routing = "paul_routing"
//  //        val _parent = None
//  //        val _createdBy = "me"
//  //        val _updatedBy = None
//  //        val _createdAt = new Date(1499438917)
//  //        val _updatedAt = None
//  //        val _model = null
//  //      }
//  //      writes.writes(personEntity).fields must containTheSameElementsAs(Json.obj(
//  //        "_id" → "Paul42",
//  //        "_routing" → "paul_routing",
//  //        "_parent" → JsNull,
//  //        "_createdBy" → "me",
//  //        "_createdAt" → 1499438917,
//  //        "_updatedBy" → JsNull,
//  //        "_updatedAt" → JsNull,
//  //        "_type" → "personWithId",
//  //        "name" → "Paul",
//  //        "age" → 42).fields)
//  //    }
//  //
//  //  }
//
//  "Model macro with metrics attribute" should {
//    case class PersonWithMetrics(
//      name: String,
//      age: Int,
//      metrics: Metrics)
//    //    "build database attributes" in {
//    //      val personDatabaseAttributes = mkDatabaseAttributes[PersonWithMetrics]
//    //      personDatabaseAttributes.map(_.name) must_=== Seq(
//    //        "name", "age", "metrics")
//    //    }
//
//    "build database JSON writer" in {
//      val writes: DatabaseWrites[PersonWithMetrics] = getDatabaseWrites[PersonWithMetrics]
//      writes(PersonWithMetrics("Paul", 42, Metrics("height" → 180, "weight" → 70))) must_===
//        Success(Some(Json.obj(
//          "name" → "Paul",
//          "age" → 42,
//          "metrics" → Json.obj(
//            "height" → 180,
//            "weight" → 70))))
//    }
//
//    "build database JSON reader" in {
//      val reads: DatabaseReads[PersonWithMetrics] = getDatabaseReads[PersonWithMetrics]
//      val personJson = Json.obj(
//        //        "_routing" → "paul_routing",
//        //        "_createdBy" → "me",
//        //        "_createdAt" → 1499438917,
//        //        "_id" → "paul_id",
//        "name" → "Paul",
//        "age" → 42,
//        "metrics" → Json.obj(
//          "height" → 180,
//          "weight" → 70))
//
//      val expectedPerson = new PersonWithMetrics("Paul", 42, Metrics("height" → 180, "weight" → 70))
//      //        with Entity {
//      //        val _id = "paul_id"
//      //        val _routing = "paul_routing"
//      //        val _parent = None
//      //        val _createdBy = "me"
//      //        val _updatedBy = None
//      //        val _createdAt = new Date(1499438917)
//      //        val _updatedAt = None
//      //        val _model = null
//      //      }
//
//      reads(Some(personJson)) match {
//        case Success(person) ⇒
//          person must_=== expectedPerson
//        //          person._id must_=== "paul_id"
//        //          person._routing must_=== "paul_routing"
//        //          person._parent must beNone
//        //          person._createdBy must_=== "me"
//        //          person._updatedBy must beNone
//        //          person._createdAt must_=== new Date(1499438917)
//        //          person._updatedAt must beNone
//        //person._model must_=== ???
//        case Failure(e) ⇒ ko(e.toString)
//      }
//
//    }
//
//    "build output JSON writer" in {
//      val writes: OWrites[PersonWithMetrics with Entity] = getEntityJsonWrites[PersonWithMetrics]
//      val personEntity = new PersonWithMetrics("Paul", 42, Metrics("height" → 180, "weight" → 70)) with Entity {
//        val _id = "paul_id"
//        val _routing = "paul_routing"
//        val _parent = None
//        val _createdBy = "me"
//        val _updatedBy = None
//        val _createdAt = new Date(1499438917)
//        val _updatedAt = None
//        val _model = null
//      }
//      writes.writes(personEntity) must_=== Json.obj(
//        "_id" → "paul_id",
//        "_routing" → "paul_routing",
//        "_parent" → JsNull,
//        "_createdBy" → "me",
//        "_createdAt" → 1499438917,
//        "_updatedBy" → JsNull,
//        "_updatedAt" → JsNull,
//        "_type" → "personWithMetrics",
//        "name" → "Paul",
//        "age" → 42,
//        "metrics" → Json.obj(
//          "height" → 180,
//          "weight" → 70))
//    }
//  }
//}
