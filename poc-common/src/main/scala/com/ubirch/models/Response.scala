package com.ubirch.models

import cats.data.NonEmptyChain
import com.ubirch.models.NOK.BAD_REQUEST

/**
  * Represents a simple Response object. Used for HTTP responses.
  */
abstract class Response[T] {
  val version: String
  val ok: T
}

object Response {
  val version = "1.0"
}

/**
  *  Represents an Error Response.
  * @param version the version of the response
  * @param ok the status of the response. true or false
  * @param errorType the error type
  * @param errorMessage the message for the response
  */
case class NOK(version: String, ok: Boolean, errorType: Symbol, errorMessage: String) extends Response[Boolean]

/**
  * Companion object for the NOK response
  */
object NOK {
  final val SERVER_ERROR = 'ServerError
  final val PARSING_ERROR = 'ParsingError
  final val NO_ROUTE_FOUND_ERROR = 'NoRouteFound
  final val DELETE_ERROR = 'TokenDeleteError
  final val AUTHENTICATION_ERROR = 'AuthenticationError
  final val NOT_ALLOWED_ERROR = 'NotAllowedError
  final val RESOURCE_NOT_FOUND_ERROR = 'ResourceNotFoundError
  final val BAD_REQUEST = 'BadRequest
  final val CONFLICT = 'Conflict

  def badRequest(errorMessage: String): NOK = NOK(BAD_REQUEST, errorMessage)

  def conflict(errorMessage: String): NOK = NOK(CONFLICT, errorMessage)

  def serverError(errorMessage: String): NOK = NOK(SERVER_ERROR, errorMessage)

  def parsingError(errorMessage: String): NOK = NOK(PARSING_ERROR, errorMessage)

  def noRouteFound(errorMessage: String): NOK = NOK(NO_ROUTE_FOUND_ERROR, errorMessage)

  def notAllowedError(errorMessage: String): NOK = NOK(NOT_ALLOWED_ERROR, errorMessage)

  def authenticationError(errorMessage: String): NOK = NOK(AUTHENTICATION_ERROR, errorMessage)

  def resourceNotFoundError(errorMessage: String): NOK = NOK(RESOURCE_NOT_FOUND_ERROR, errorMessage)

  def apply(errorType: Symbol, errorMessage: String): NOK =
    new NOK(Response.version, ok = false, errorType, errorMessage)

}

case class Return(version: String, ok: Boolean, data: Any) extends Response[Boolean]

object Return {
  def apply(data: Any): Return = new Return(Response.version, ok = true, data)
  def apply(ok: Boolean, data: Any): Return = new Return(Response.version, ok = ok, data)
}

case class ValidationErrorsResponse(
  version: String,
  ok: Boolean,
  errorType: Symbol,
  validationErrors: Seq[FieldError])
  extends Response[Boolean]

case class FieldError(name: String, error: String)

object ValidationErrorsResponse {
  def apply(validationErrors: Map[String, String]): ValidationErrorsResponse =
    ValidationErrorsResponse(
      version = Response.version,
      ok = false,
      errorType = BAD_REQUEST,
      validationErrors = validationErrors.map { case (field, error) => FieldError(field, error) }.toSeq)
}

case class Paginated_OUT[T](total: Long, records: Seq[T])
case class ValidationError(n: NonEmptyChain[(String, String)]) extends RuntimeException(s"Validation errors occurred")