package org.elastic4play.macros

import java.nio.file.Paths
import java.util.Date

import org.elastic4play.models._
import org.elastic4play.services.AttachmentSrv
import org.elastic4play.utils.Hash
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

@EntityModel
case class GrandParent(name: String)
@EntityModel
@WithParent[GrandParent]
case class Parent(name: String)
@EntityModel
@WithParent[Parent]
case class Child(name: String, attachment: Attachment)

class ModelTest  extends Specification with TestUtils with Mockito {

  "model" should {
    "have parents" in {
      Child.model.parents must_=== Parent.model :: GrandParent.model :: Nil
    }

    "read data from database" in {
      val jsdata = Json.obj(
        "name" -> "childEntity",
        "attachment" -> Json.obj(
          "name" -> "filename.txt",
          "hashes" -> Json.arr("deadbeef01", "deadbeef02"),
          "size" -> 453,
          "contentType" -> "text/plain",
          "id" -> "attachmentId"
        ),
        "_id" -> "child_id",
        "_routing" -> "child_routing",
        "_parent" -> "parent_id",
        "_createdAt" -> 1507468476321L,
      "_createdBy" -> "me"
      )
      val expectedChild = new Child("childEntity", FAttachment("filename.txt", Seq(Hash("deadbeef01"), Hash("deadbeef02")), 453L, "text/plain", "attachmentId")) with Entity {
        val _id = "child_id"
        val _routing = "child_routing"
        val _parent = Some("parent_id")
        val _type = "Child"
        val _model = Child.model
        val _createdAt = new Date(1507468476321L)
        val _createdBy = "me"
        val _updatedAt = None
        val _updatedBy = None
      }
      Child.model.databaseReads(Some(jsdata)) must_=== Success(expectedChild)
    }

    "save attachment" in {
      implicit val ee = ExecutionEnv.fromGlobalExecutionContext

      val file = FFile("filename.txt", Paths.get("/tmp/xxx.tmp"), "text/plain")
      val attachment = FAttachment("filename.txt", Seq(Hash("deadbeef01"), Hash("deadbeef02")), 453L, "text/plain", "attachmentId")
      val child = Child("childEntity",       file)
      val attachmentSrv = mock[AttachmentSrv]

      val expectedChild = Child("childEntity", attachment)

      attachmentSrv.save(file) returns Future.successful(attachment)
      Child.model.saveAttachment(attachmentSrv, child) must beEqualTo(expectedChild).awaitFor(1.second)
    }

    "save updated attachment" in {
      implicit val ee = ExecutionEnv.fromGlobalExecutionContext

      val file = FFile("filename.txt", Paths.get("/tmp/xxx.tmp"), "text/plain")
      val attachment = FAttachment("filename.txt", Seq(Hash("deadbeef01"), Hash("deadbeef02")), 453L, "text/plain", "attachmentId")
      val attachmentSrv = mock[AttachmentSrv]

      attachmentSrv.save(file) returns Future.successful(attachment)
      val updates = Map(FPath("attachment") -> UpdateOps.SetAttribute(file), FPath("name") -> UpdateOps.SetAttribute("otherName"))
      val expectedUpdates = Map(FPath("attachment") -> UpdateOps.SetAttribute(attachment), FPath("name") -> UpdateOps.SetAttribute("otherName"))
      Child.model.saveUpdateAttachment(attachmentSrv, updates) must beEqualTo(expectedUpdates).awaitFor(1.second)
    }
  }
}
