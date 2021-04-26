package com.ubirch.models

/** Represents a simple Response object. Used for HTTP responses.
  */
abstract class Response[T] {
  val version: String
  val ok: T
}

object Response {
  val version = "1.0"
}

/**  Represents an Error Response.
  * @param version the version of the response
  * @param ok the status of the response. true or false
  * @param errorType the error type
  * @param errorMessage the message for the response
  */
case class NOK(version: String, ok: Boolean, errorType: Symbol, errorMessage: String) extends Response[Boolean]

/** Companion object for the NOK response
  */
object NOK {

  final val SERVER_ERROR = 'ServerError
  final val PARSING_ERROR = 'ParsingError
  final val NO_ROUTE_FOUND_ERROR = 'NoRouteFound
  final val DELETE_ERROR = 'TokenDeleteError
  final val AUTHENTICATION_ERROR = 'AuthenticationError

  def apply(errorType: Symbol, errorMessage: String): NOK =
    new NOK(Response.version, ok = false, errorType, errorMessage)

  def serverError(errorMessage: String): NOK = NOK(SERVER_ERROR, errorMessage)
  def parsingError(errorMessage: String): NOK = NOK(PARSING_ERROR, errorMessage)
  def noRouteFound(errorMessage: String): NOK = NOK(NO_ROUTE_FOUND_ERROR, errorMessage)
  def authenticationError(errorMessage: String): NOK = NOK(AUTHENTICATION_ERROR, errorMessage)

}

case class Return(version: String, ok: Boolean, data: Any) extends Response[Boolean]

object Return {
  def apply(data: Any): Return = new Return(Response.version, ok = true, data)
  def apply(ok: Boolean, data: Any): Return = new Return(Response.version, ok = ok, data)
}
