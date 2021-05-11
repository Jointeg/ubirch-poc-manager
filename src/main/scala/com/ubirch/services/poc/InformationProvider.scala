package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.poc.{ Poc, PocStatus }
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{ basicRequest, SttpBackend, UriContext }
import sttp.model.StatusCode.{ Conflict, Ok }

import scala.concurrent.Future

trait InformationProvider {

  def infoToGoClient(poc: Poc, status: StatusAndPW): Task[StatusAndPW]

  def infoToCertifyAPI(poc: Poc, status: StatusAndPW): Task[PocStatus]

}

case class RegisterDeviceGoClient(uuid: String, password: String)
case class RegisterDeviceCertifyAPI(name: String, deviceId: String, password: String, role: String, cert: String)

class InformationProviderImpl @Inject() (conf: Config)(implicit formats: Formats)
  extends InformationProvider
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  protected val goClientURL: String = conf.getString(ServicesConfPaths.GO_CLIENT_URL)
  private val goClientToken: String = conf.getString(ServicesConfPaths.GO_CLIENT_TOKEN)
  protected val certifyApiURL: String = conf.getString(ServicesConfPaths.CERTIFY_API_URL)
  private val certifyApiToken: String = conf.getString(ServicesConfPaths.CERTIFY_API_TOKEN)
  private val xAuthHeaderKey: String = "X-Auth-Token"
  private val contentTypeHeaderKey: String = "Content-Type"
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  @throws[PocCreationError]
  override def infoToGoClient(poc: Poc, statusAndPW: StatusAndPW): Task[StatusAndPW] = {
    val status = statusAndPW.pocStatus
    if (status.goClientProvided) {
      Task(statusAndPW)
    } else {
      val body = getGoClientBody(poc, statusAndPW)
      Task
        .fromFuture(goClientRequest(statusAndPW, body))
        .onErrorHandle(ex => throwAndLogError(status, "an error occurred when providing info to go client; ", ex))
    }
  }

  @throws[PocCreationError]
  protected def goClientRequest(statusAndPW: StatusAndPW, body: String): Future[StatusAndPW] = {
    Future(
      basicRequest
        .put(uri"$goClientURL")
        .header(xAuthHeaderKey, goClientToken)
        .header(contentTypeHeaderKey, "application/json")
        .body(body)
        .send()
        .map {
          _.code match {
            case Ok =>
              StatusAndPW(statusAndPW.pocStatus.copy(goClientProvided = true), statusAndPW.devicePassword)
            case Conflict =>
              StatusAndPW(statusAndPW.pocStatus.copy(goClientProvided = true), statusAndPW.devicePassword)
            case code =>
              throwError(statusAndPW.pocStatus, s"failure when providing device info to goClient, statusCode: $code")
          }
        }
    ).flatten
  }

  @throws[PocCreationError]
  override def infoToCertifyAPI(poc: Poc, statusAndPW: StatusAndPW): Task[PocStatus] = {
    val status = statusAndPW.pocStatus
    if (status.certifyApiProvided) {
      Task(status)
    } else {
      val body = getCertifyApiBody(poc, statusAndPW)
      Task
        .fromFuture(certifyApiRequest(statusAndPW, body))
        .onErrorHandle(ex => throwAndLogError(status, "an error occurred when providing info to certify api; ", ex))
    }
  }

  @throws[PocCreationError]
  protected def certifyApiRequest(statusAndPW: StatusAndPW, body: String): Future[PocStatus] = {
    Future(
      basicRequest
        .put(uri"$certifyApiURL")
        .body(body)
        .header(xAuthHeaderKey, certifyApiToken)
        .header(contentTypeHeaderKey, "application/json")
        .send()
        .map {
          _.code match {
            case Ok =>
              statusAndPW.pocStatus.copy(certifyApiProvided = true)
            case code =>
              throwError(statusAndPW.pocStatus, s"failure when providing device info to certifyAPI, errorCode: $code")
          }
        }
    ).flatten
  }

  private def getCertifyApiBody(poc: Poc, statusAndPW: StatusAndPW): String = {

    val registerDevice =
      RegisterDeviceCertifyAPI(
        poc.pocName,
        poc.getDeviceId,
        statusAndPW.devicePassword,
        poc.roleName,
        poc.roleName)
    write[RegisterDeviceCertifyAPI](registerDevice)
  }

  private def getGoClientBody(poc: Poc, statusAndPW: StatusAndPW): String = {
    val registerDevice = RegisterDeviceGoClient(poc.getDeviceId, statusAndPW.devicePassword)
    write[RegisterDeviceGoClient](registerDevice)
  }

  @throws[PocCreationError]
  private def throwError(status: PocStatus, msg: String): Nothing = {
    throw PocCreationError(status.copy(errorMessage = Some(msg)))
  }

  private def throwAndLogError(status: PocStatus, msg: String, ex: Throwable): Nothing = {
    logger.error(msg, ex)
    throwError(status, msg + ex.getMessage)
  }

}
