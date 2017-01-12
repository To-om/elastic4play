package org.elastic4play.services

import javax.inject.{ Inject, Singleton }

import scala.concurrent.Future

import play.api.libs.iteratee.Execution.trampoline
import play.api.libs.json.{ JsObject, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.test.PlaySpecification

import org.elastic4play.NotFoundError
import org.elastic4play.database.DBRemove
import org.elastic4play.models.{ AttributeFormat ⇒ F, EntityDef, ModelDef }
import org.elastic4play.utils.RichFuture
import org.junit.runner.RunWith
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DeleteSrvSpec extends PlaySpecification with Mockito {

  class TestModel extends ModelDef[TestModel, TestEntity]("testModel") {
    val textAttribute = attribute("textAttribute", F.textFmt, "textAttribute")
    val stringAttribute = attribute("stringAttribute", F.stringFmt, "stringAttribute")
    val dateAttribute = attribute("dateAttribute", F.dateFmt, "dateAttribute")
    val booleanAttribute = attribute("booleanAttribute", F.booleanFmt, "booleanAttribute")
    val uuidAttribute = attribute("uuidAttribute", F.uuidFmt, "uuidAttribute")
    val hashAttribute = attribute("hashAttribute", F.hashFmt, "hashAttribute")
    val metricAttribute = attribute("metricAttribute", F.metricsFmt, "metricAttribute")
  }
  class TestEntity(model: TestModel, attributes: JsObject) extends EntityDef[TestModel, TestEntity](model, attributes)

  implicit val ec = trampoline
  implicit val authContext = mock[AuthContext]
  val updateSrv = mock[UpdateSrv]
  val getSrv = mock[GetSrv]
  val dbRemove = mock[DBRemove]
  val eventSrv = mock[EventSrv]
  val deleteSrv = new DeleteSrv(updateSrv, getSrv, dbRemove, eventSrv, trampoline)
  val model = new TestModel
  val entity = new TestEntity(model, Json.obj(
    "_id" → "42",
    "_routing" → "42",
    "textAttribute" → "valid text",
    "stringAttribute" → "valid string",
    "dateAttribute" → "20160128T175800+0100",
    "booleanAttribute" → true,
    "uuidAttribute" → "ee0caf69-560b-4453-9bae-72982225e661",
    "hashAttribute" → "01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b",
    "metricAttribute" → Json.obj("metric1" → 1, "metric2" → 2),
    "user" → "testUser",
    "createdAt" → "20160620T162845+0200",
    "createdBy" → "testUser"))

  "DeleteSrv.realDelete" should {
    "remove entity if exists" in {
      val id = "42"
      getSrv[TestModel, TestEntity](model, id) returns Future.successful(entity)
      dbRemove(model, entity) returns Future.successful(true)
      deleteSrv.realDelete[TestModel, TestEntity](model, id).await must_== (())
      there was one(dbRemove).apply(model, entity)
    }

    "returns error if entity can't be retrieve" in {
      val id = "42"
      val error = NotFoundError(s"${model.name} $id not found")
      getSrv[TestModel, TestEntity](model, id) returns Future.failed(error)
      deleteSrv.realDelete[TestModel, TestEntity](model, id).await must throwA[NotFoundError]
    }

    "returns error if entity is not found" in {
      val id = "42"
      getSrv[TestModel, TestEntity](model, id) returns Future.successful(entity)
      dbRemove(model, entity) returns Future.successful(false)
      deleteSrv.realDelete[TestModel, TestEntity](model, id).await must throwA[NotFoundError]
    }
  }

}