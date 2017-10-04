package org.elastic4play

import org.elastic4play.models.Field
import play.api.libs.json.JsObject

case class BadRequestError(message: String) extends Exception(message)
case class CreateError(status: Option[String], message: String, attributes: JsObject) extends Exception(message)
case class NotFoundError(message: String) extends Exception(message)
case class GetError(message: String) extends Exception(message)
case class UpdateError(status: Option[String], message: String, attributes: JsObject) extends Exception(message)
case class InternalError(message: String) extends Exception(message)
case class SearchError(message: String, cause: Throwable) extends Exception(message, cause)
case class AuthenticationError(message: String) extends Exception(message)
case class AuthorizationError(message: String) extends Exception(message)
case class MultiError(message: String, exceptions: Seq[Exception]) extends Exception(message + exceptions.map(_.getMessage).mkString(" :\n\t- ", "\n\t- ", ""))

case class AttributeCheckingError(errors: Seq[AttributeError] = Nil)
    extends Exception(errors.mkString("[", "][", "]")) {
  override def toString: String = errors.mkString("[", "][", "]")
}

sealed trait AttributeError extends Throwable {
  val name: String
  def withName(name: String): AttributeError
  def withModel(model: String): AttributeError
}

case class InvalidFormatAttributeError(name: String, format: String, field: Field) extends AttributeError {
  override def toString = s"Invalid format for $name: $field, expected $format"
  override def withName(newName: String): InvalidFormatAttributeError = copy(name = newName)
  override def withModel(model: String): InvalidFormatAttributeError = copy(name = s"$model.$name")
}
case class UnknownAttributeError(name: String, field: Field) extends AttributeError {
  override def toString = s"Unknown attribute $name: $field"
  override def withName(newName: String): UnknownAttributeError = copy(name = newName)
  override def withModel(model: String): UnknownAttributeError = copy(name = s"$model.$name")
}
case class UpdateReadOnlyAttributeError(name: String) extends AttributeError {
  override def toString = s"Attribute $name is read-only"
  override def withName(newName: String): UpdateReadOnlyAttributeError = copy(name = newName)
  override def withModel(model: String): UpdateReadOnlyAttributeError = copy(name = s"$model.$name")
}
case class MissingAttributeError(name: String) extends AttributeError {
  override def toString = s"Attribute $name is missing"
  override def withName(newName: String): MissingAttributeError = copy(name = newName)
  override def withModel(model: String): MissingAttributeError = copy(name = s"$model.$name")
}
case class UnsupportedAttributeError(name: String) extends AttributeError {
  override def toString = s"Attribute $name is not supported"
  override def withName(newName: String): UnsupportedAttributeError = copy(name = newName)
  override def withModel(model: String): UnsupportedAttributeError = copy(name = s"$model.$name")
}