package org.elastic4play.controllers

import org.specs2.concurrent.ExecutionEnv
import org.specs2.mock.Mockito
import play.api.libs.json.Json
import play.api.mvc.{ AnyContentAsJson, DefaultActionBuilder, Results }
import play.api.test.{ FakeRequest, Helpers, PlaySpecification }

import org.elastic4play.macros.SimpleClassForFieldsParserMacroTest
import org.elastic4play.models.FieldsParser

class ControllerTest extends PlaySpecification with Mockito {

  "controller" should {

    "extract simple class from HTTP request" in {
      implicit val ee: ExecutionEnv = ExecutionEnv.fromGlobalExecutionContext

      val actionBuilder = DefaultActionBuilder(Helpers.stubBodyParser())
      val apiMethod = new ApiMethod(
        mock[Authenticated],
        actionBuilder,
        ee.ec)

      val action = apiMethod("model extraction").extract("simpleClass", FieldsParser[SimpleClassForFieldsParserMacroTest]) { req ⇒
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
  }

}