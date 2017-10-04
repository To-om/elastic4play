package org.elastic4play.macros

import org.scalactic.Good

import org.elastic4play.models.{ FPath, FString, FieldsParser, UpdateFieldsParser, WithParser, WithUpdateParser }

case class SimpleClassForFieldsParserMacroTest(name: String, value: Int)

case class SubClassForFieldsParserMacroTest(name: String, option: Option[Int])
case class ComplexClassForFieldsParserMacroTest(name: String, value: Int, subClasses: Seq[SubClassForFieldsParserMacroTest])

@WithParser(CustomFieldsParsers.englishIntFieldsParser)
@WithUpdateParser(CustomFieldsParsers.englishUpdateFieldsParser)
case class LocaleInt(value: Int)
object CustomFieldsParsers {
  val englishIntFieldsParser: FieldsParser[LocaleInt] = FieldsParser[LocaleInt]("englishInt") {
    case (_, FString("one"))   ⇒ Good(LocaleInt(1))
    case (_, FString("two"))   ⇒ Good(LocaleInt(2))
    case (_, FString("three")) ⇒ Good(LocaleInt(3))
  }
  val frenchIntFieldsParser: FieldsParser[LocaleInt] = FieldsParser[LocaleInt]("frenchInt") {
    case (_, FString("un"))    ⇒ Good(LocaleInt(1))
    case (_, FString("deux"))  ⇒ Good(LocaleInt(2))
    case (_, FString("trois")) ⇒ Good(LocaleInt(3))
  }
  val englishUpdateFieldsParser: UpdateFieldsParser[LocaleInt] = UpdateFieldsParser[LocaleInt]("englishLocalInt")(
    FPath.empty → englishIntFieldsParser.toUpdate)
  val frenchUpdateFieldsParser: UpdateFieldsParser[LocaleInt] = UpdateFieldsParser[LocaleInt]("frenchLocalInt")(
    FPath.empty → frenchIntFieldsParser.toUpdate)
}
case class ClassWithAnnotation(
  name: String,
  @WithParser(CustomFieldsParsers.frenchIntFieldsParser)@WithUpdateParser(CustomFieldsParsers.frenchUpdateFieldsParser) valueFr: LocaleInt,
  valueEn: LocaleInt)
