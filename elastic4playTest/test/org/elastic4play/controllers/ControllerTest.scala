package org.elastic4play.controllers

import scala.concurrent.Await
import scala.concurrent.duration._

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import play.api.libs.json.Json
import play.api.mvc.{ AnyContentAsJson, DefaultActionBuilder, Results }
import play.api.test.{ FakeRequest, Helpers, PlaySpecification }

import akka.stream.Materializer
import akka.stream.scaladsl.Source

import org.elastic4play.macros.SimpleClassForFieldsParserMacroTest
import org.elastic4play.models.FieldsParser

class ControllerTest extends PlaySpecification with Mockito {
  lazy val app: Application = new GuiceApplicationBuilder().build()
  implicit lazy val mat: Materializer = app.materializer
  //implicit lazy val as: ActorSystem = app.actorSystem
  implicit val ee: ExecutionEnv = ExecutionEnv.fromGlobalExecutionContext

  "controller" should {

    "extract simple class from HTTP request" in {

      val actionBuilder = DefaultActionBuilder(Helpers.stubBodyParser())
      val apiMethod = new ApiMethod(
        mock[Authenticated],
        actionBuilder,
        ee.ec,
        mat)

      val action = apiMethod("model extraction")
        .extract("simpleClass", FieldsParser[SimpleClassForFieldsParserMacroTest]) { req ⇒
          val simpleClass = req.body("simpleClass")
          simpleClass must_=== SimpleClassForFieldsParserMacroTest("myName", 44)
          Results.Ok("ok")
        }

      val request = FakeRequest("POST", "/api/simple_class").withBody(AnyContentAsJson(Json.obj(
        "name" → "myName",
        "value" → 44)))
      val result = action(request)
      val bodyText = contentAsString(result)
      bodyText must be equalTo "ok"
    }

    "render stream with total number of element in header" in {

      val actionBuilder = DefaultActionBuilder(Helpers.stubBodyParser())
      val apiMethod = new ApiMethod(
        mock[Authenticated],
        actionBuilder,
        ee.ec,
        mat)

      val action = apiMethod("find entity")
        .chunked { implicit request ⇒
          Source(0 to 3)
            .mapMaterializedValue(_ ⇒ 10)
        }
      val request = FakeRequest("GET", "/")
      val result = Await.result(action(request), 1.second)
      result.header.headers("X-Total") must_=== "10"
      result.body.contentType must beSome("application/json")
      Await.result(result.body.consumeData.map(_.decodeString("utf-8")), 1.second) must_=== Json.arr(0, 1, 2, 3).toString
    }
  }

}