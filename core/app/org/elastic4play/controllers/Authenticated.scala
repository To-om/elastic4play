package org.elastic4play.controllers

import java.util.Date
import javax.inject.{ Inject, Singleton }

import org.elastic4play.AuthenticationError
import org.elastic4play.services.{ AuthContext, AuthSrv, Role, UserSrv }
import org.elastic4play.utils.Instance
import play.api.http.HeaderNames
import play.api.mvc._
import play.api.{ Configuration, Logger }

import scala.concurrent.duration.{ DurationLong, FiniteDuration }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

/**
 * A request with authentication information
 *
 * @param authContext authentication information (which contains user name, roles, ...)
 * @param request the request
 * @tparam A the body content type.
 */
class AuthenticatedRequest[A](val authContext: AuthContext, request: Request[A]) extends WrappedRequest[A](request) with AuthContext with Request[A] {
  override def userId: String = authContext.userId
  override def userName: String = authContext.userName
  override def requestId: String = Instance.getRequestId(request)
  override def roles: Seq[Role.Type] = authContext.roles
  override def map[B](f: A ⇒ B): AuthenticatedRequest[B] = new AuthenticatedRequest(authContext, request.map(f))
}

object ExpirationStatus {
  sealed abstract class Type
  case class Ok(duration: FiniteDuration) extends Type
  case class Warning(duration: FiniteDuration) extends Type
  case object Error extends Type
}

/**
 * Check and manager user security (authentication and authorization)
 *
 * @param maxSessionInactivity maximum time a session without any activity stay valid
 * @param sessionWarning
 * @param sessionUsername
 * @param userSrv
 * @param authSrv
 * @param defaultParser
 * @param ec
 */
@Singleton
class Authenticated(
    maxSessionInactivity: FiniteDuration,
    sessionWarning: FiniteDuration,
    sessionUsername: String,
    userSrv: UserSrv,
    authSrv: AuthSrv,
    defaultParser: BodyParsers.Default,
    implicit val ec: ExecutionContext) {

  @Inject() def this(
    configuration: Configuration,
    userSrv: UserSrv,
    authSrv: AuthSrv,
    defaultParser: BodyParsers.Default,
    ec: ExecutionContext) =
    this(
      configuration.getMillis("session.inactivity").millis,
      configuration.getMillis("session.warning").millis,
      configuration.get[String]("session.username"),
      userSrv,
      authSrv,
      defaultParser,
      ec)

  lazy val logger = Logger(getClass)

  private def now = (new Date).getTime

  /**
   * Insert or update session cookie containing user name and session expiration timestamp
   * Cookie is signed by Play framework (it cannot be modified by user)
   */
  def setSessingUser(result: Result, authContext: AuthContext)(implicit request: RequestHeader): Result =
    result.addingToSession(sessionUsername → authContext.userId, "expire" → (now + maxSessionInactivity.toMillis).toString)

  /**
   * Retrieve authentication information form cookie
   */
  def getFromSession(request: RequestHeader): Future[AuthContext] = {
    val userId = for {
      userId ← request.session.get(sessionUsername)
      if expirationStatus(request) != ExpirationStatus.Error
    } yield userId
    userId.fold(Future.failed[AuthContext](AuthenticationError("Not authenticated")))(id ⇒ userSrv.getFromId(request, id))
  }

  def expirationStatus(request: RequestHeader): ExpirationStatus.Type = {
    request.session.get("expire")
      .flatMap { expireStr ⇒
        Try(expireStr.toLong).toOption
      }
      .map { expire ⇒ (expire - now).millis }
      .map {
        case duration if duration.length < 0       ⇒ ExpirationStatus.Error
        case duration if duration < sessionWarning ⇒ ExpirationStatus.Warning(duration)
        case duration                              ⇒ ExpirationStatus.Ok(duration)
      }
      .getOrElse(ExpirationStatus.Error)
  }

  /**
   * Retrieve authentication information from API key
   */
  def getFromApiKey(request: RequestHeader): Future[AuthContext] =
    request
      .headers
      .get(HeaderNames.AUTHORIZATION)
      .collect {
        case auth if auth.startsWith("Basic ") ⇒
          logger.info(s"Found basic auth")
          val authWithoutBasic = auth.substring(6)
          val decodedAuth = new String(java.util.Base64.getDecoder.decode(authWithoutBasic), "UTF-8")
          decodedAuth.split(":")
      }
      .collect {
        case Array(username, password) ⇒ authSrv.authenticate(username, password)(request)
      }
      .getOrElse(Future.failed[AuthContext](new Exception("TODO")))

  /**
   * Get user in session -orElse- get user from key parameter
   */
  def getContext(request: RequestHeader): Future[AuthContext] =
    getFromSession(request)
      .fallbackTo(getFromApiKey(request))
      .fallbackTo(userSrv.getInitialUser(request))
      .recoverWith { case _ ⇒ Future.failed(AuthenticationError("Not authenticated")) }

  /**
   * Create an action for authenticated controller
   * If user has sufficient right (have required role) action is executed
   * otherwise, action returns a not authorized error
   */
  def apply(requiredRole: Role.Type) = new ActionBuilder[AuthenticatedRequest, AnyContent] {
    val executionContext: ExecutionContext = ec

    def invokeBlock[A](request: Request[A], block: (AuthenticatedRequest[A]) ⇒ Future[Result]): Future[Result] = {
      getContext(request).flatMap { authContext ⇒
        if (authContext.roles.contains(requiredRole))
          block(new AuthenticatedRequest(authContext, request))
            .map(result ⇒ setSessingUser(result, authContext)(request))
        else
          Future.failed(new Exception(s"Insufficient rights to perform this action"))
      }
    }

    def parser: BodyParser[AnyContent] = defaultParser
  }
}