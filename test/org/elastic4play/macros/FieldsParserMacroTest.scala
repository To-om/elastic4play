package org.elastic4play.macros

import org.elastic4play.models.{ FNull, FNumber, FObject, FSeq, FString, FieldsParser }
import org.scalactic.Good
import org.specs2.mutable.Specification

class FieldsParserMacroTest extends Specification with TestUtils {

  "FieldParser macro" should {

    "have a name" in {
      val fieldsParser = getFieldsParser[SimpleClassForFieldsParserMacroTest]
      fieldsParser.formatName must_=== "SimpleClassForFieldsParserMacroTest"
    }

    "parse a simple class" in {
      val fieldsParser = getFieldsParser[SimpleClassForFieldsParserMacroTest]
      val fields = FObject(
        "name" → FString("simpleClass"),
        "value" → FNumber(42))
      val simpleClass = SimpleClassForFieldsParserMacroTest("simpleClass", 42)
      fieldsParser(fields) must_=== Good(simpleClass)
    }

    "parse complex class" in {
      val fieldsParser = getFieldsParser[ComplexClassForFieldsParserMacroTest]
      val fields = FObject(
        "name" → FString("complexClass"),
        "value" → FNumber(42),
        "subClasses" → FSeq(
          FObject("name" → FString("sc1"), "option" → FNumber(12)),
          FObject("name" → FString("sc2"), "option" → FNull)))
      val complexClass = ComplexClassForFieldsParserMacroTest("complexClass", 42, Seq(SubClassForFieldsParserMacroTest("sc1", Some(12)), SubClassForFieldsParserMacroTest("sc2", None)))
      fieldsParser(fields) must_=== Good(complexClass)
    }

    "parse class with annotation" in {
      val fieldsParser = getFieldsParser[ClassWithAnnotation]
      val fields = FObject(
        "name" → FString("classWithAnnotation"),
        "valueFr" → FString("un"),
        "valueEn" → FString("three"))
      val classWithAnnotation = ClassWithAnnotation("classWithAnnotation", LocaleInt(1), LocaleInt(3))
      fieldsParser(fields) must_=== Good(classWithAnnotation)
    }

    "parse class with implicit" in {
      val subClassRegex = "(\\w+),(\\d+)".r
      implicit val subClassFieldsParser: FieldsParser[SubClassForFieldsParserMacroTest] = FieldsParser[SubClassForFieldsParserMacroTest]("SubClass") {
        case (_, FString(subClassRegex(name, value))) ⇒ Good(SubClassForFieldsParserMacroTest(name, Some(value.toInt)))
        case (_, FString(name))                       ⇒ Good(SubClassForFieldsParserMacroTest(name, None))
      }
      val fieldsParser = getFieldsParser[ComplexClassForFieldsParserMacroTest]
      val fields = FObject(
        "name" → FString("complexClass"),
        "value" → FNumber(42),
        "subClasses" → FSeq(
          FString("sc1,12"),
          FString("sc2")))
      val complexClass = ComplexClassForFieldsParserMacroTest("complexClass", 42, Seq(SubClassForFieldsParserMacroTest("sc1", Some(12)), SubClassForFieldsParserMacroTest("sc2", None)))
      fieldsParser(fields) must_=== Good(complexClass)
    }

    "parse class with multi attachments in sub fields" in {
      todo
    }
  }
}