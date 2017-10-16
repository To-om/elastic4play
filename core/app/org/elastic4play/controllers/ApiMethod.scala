package org.elastic4play.controllers

import javax.inject.{ Inject, Singleton }

import akka.stream.scaladsl.{ Keep, Sink, Source }

import org.elastic4play.JsonFormat._
import org.elastic4play.models.{ BaseFieldsParser, Field }
import org.elastic4play.services.Role
import org.elastic4play.utils.Record
import org.elastic4play.AttributeCheckingError
import org.scalactic.{ Bad, Good }
import shapeless.{ HList, HNil, Witness, labelled }
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.higherKinds

import play.api.mvc._
import play.api.Logger
import play.api.libs.json.{ Json, Writes }
import play.api.mvc.Results.BadRequest

import akka.stream.Materializer
/**
 * API entry point. This class create a controller action which parse request and check authentication
 *
 * @param authenticated method that check user authentication
 * @param actionBuilder ActionBuilder
 * @param ec ExecutionContext
 */
@Singleton
class ApiMethod @Inject() (
    authenticated: Authenticated,
    actionBuilder: DefaultActionBuilder,
    implicit val ec: ExecutionContext,
    implicit val mat: Materializer) {

  lazy val logger = Logger(getClass)

  /**
   * Create a named entry point
   *
   * @param name name of entry point
   * @return empty entry point
   */
  def apply(name: String): EntryPoint[HNil, Request] = EntryPoint[HNil, Request](name, BaseFieldsParser.empty(HNil), Future.successful)

  /**
   * An entry point is defined by its name, a fields parser which transform request into a record V and the type of request (
   * authenticated or not)
   *
   * @param name name of the entry point
   * @param fieldsParser fields parser that transform request into a record of type V
   * @param req request transformer function
   * @tparam V type of record
   * @tparam R type of request (Request of AuthenticatedRequest)
   */
  case class EntryPoint[V <: HList, R[_] <: Request[_]](
      name: String,
      fieldsParser: BaseFieldsParser[V],
      req: Request[AnyContent] ⇒ Future[R[AnyContent]]) {

    /**
     * Extract a field from request.
     *
     * @param fp field parser to use to extract value from request
     * @tparam T type of extracted field
     * @return a new entry point with added fields parser
     */
    def extract[N, T](fieldName: Witness.Aux[N], fp: BaseFieldsParser[T]) = EntryPoint(
      name,
      fieldsParser.andThen(fieldName.toString)(fp)(labelled.field[N](_) :: _),
      req)

    /**
     * Add an authentication check to this entry point. If user doesn't have required roles, resulting action answers with error.
     *
     * @param requiredRoles role that user must have to accomplish this action
     * @return a new entry point with added authentication check
     */
    def requires(requiredRoles: Role.Type*): EntryPoint[V, AuthenticatedRequest] = {
      EntryPoint[V, AuthenticatedRequest](name, fieldsParser, request ⇒ {
        authenticated.getContext(request).map { authContext ⇒
          logger.trace(s"check user role of ${authContext.userName} with roles (${authContext.roles.mkString(",")}), required: ${requiredRoles.mkString(",")}")
          if ((requiredRoles.toSet -- authContext.roles).isEmpty)
            new AuthenticatedRequest[AnyContent](authContext, request)
          else
            sys.error("plop")
        }
      })
    }

    /**
     * Materialize action using a function that transform request with parsed record info stream of writable
     *
     * @param block business logic function that transform request into stream of element
     * @tparam T type of element in stream. Element must be writable to JSON
     * @return Action
     */
    def chunked[T: Writes](block: R[Record[V]] ⇒ Source[T, Long]): Action[AnyContent] = {
      def sourceToResult(src: Source[T, Long]): Result = {

        val (numberOfElement, publisher) = src
          .map(t ⇒ Json.toJson(t).toString)
          .intersperse("[", ",", "]")
          .toMat(Sink.asPublisher(false))(Keep.both)
          .run()

        Results.Ok
          .chunked {
            Source.fromPublisher(publisher)
          }
          .as("application/json")
          .withHeaders("X-total" -> numberOfElement.toString)
      }

      actionBuilder.async { request: Request[AnyContent] ⇒
        fieldsParser(Field(request)) match {
          case Good(values) ⇒ req(request).map { r ⇒ sourceToResult(block(r.map(_ ⇒ Record(values)).asInstanceOf[R[Record[V]]])) }
          case Bad(errors)  ⇒ Future.successful(BadRequest(Json.toJson(AttributeCheckingError(errors.toSeq))))
        }
      }
    }

    /**
     * Materialize action using a function that transform request into future response
     *
     * @param block business login function that transform request into future response
     * @return Action
     */
    def async(block: R[Record[V]] ⇒ Future[Result]): Action[AnyContent] = {
      actionBuilder.async { request: Request[AnyContent] ⇒
        fieldsParser(Field(request)) match {
          case Good(values) ⇒ req(request).flatMap { r ⇒ block(r.map(_ ⇒ Record(values)).asInstanceOf[R[Record[V]]]) }
          case Bad(errors)  ⇒ Future.successful(BadRequest(Json.toJson(AttributeCheckingError(errors.toSeq))))
        }
      }
    }

    /**
     * Materialize action using a function that transform request into response
     *
     * @param block business login function that transform request into response
     * @return Action
     */
    def apply(block: R[Record[V]] ⇒ Result): Action[AnyContent] = async(r ⇒ Future.successful(block(r)))
  }
}