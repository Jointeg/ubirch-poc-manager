package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
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
import sttp.model.StatusCode.Ok

import java.util.UUID
import scala.concurrent.Future

trait InformationProvider {

  def infoToGoClient(poc: Poc, status: StatusAndPW): Task[StatusAndPW]

  def infoToCertifyAPI(poc: Poc, status: StatusAndPW): Task[PocStatus]

}

case class RegisterDeviceGoClient(deviceId: String, password: String)
case class RegisterDeviceCertifyAPI(name: String, deviceId: String, password: String, role: String, cert: String)

class InformationProviderImpl @Inject() (conf: Config)(implicit formats: Formats) extends InformationProvider {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  private val goClientURL: String = conf.getString(ServicesConfPaths.GO_CLIENT_URL)
  private val goClientToken: String = conf.getString(ServicesConfPaths.GO_CLIENT_TOKEN)
  private val certifyApiURL: String = conf.getString(ServicesConfPaths.CERTIFY_API_URL)
  private val certifyApiToken: String = conf.getString(ServicesConfPaths.CERTIFY_API_TOKEN)
  private val xAuthHeaderKey: String = "X-Auth-Token"
  private val contentTypeHeaderKey: String = "Content-Type"
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  override def infoToGoClient(poc: Poc, statusAndPW: StatusAndPW): Task[StatusAndPW] = {
    val status = statusAndPW.pocStatus
    if (status.goClientProvided) {
      Task(statusAndPW)
    } else {
      val registerDevice = RegisterDeviceGoClient(poc.deviceId.value.value.toString, statusAndPW.devicePassword)
      val body = write[RegisterDeviceGoClient](registerDevice)
      Task.fromFuture(goClientRequest(statusAndPW, body))
    }
  }

  protected def goClientRequest(statusAndPW: StatusAndPW, body: String): Future[StatusAndPW] = {
    basicRequest
      .put(uri"$goClientURL")
      .body(body)
      .header(xAuthHeaderKey, goClientToken)
      .header(contentTypeHeaderKey, "application/json")
      .send()
      .map {
        _.code match {
          case Ok =>
            StatusAndPW(statusAndPW.pocStatus.copy(goClientProvided = true), statusAndPW.devicePassword)
          case code =>
            throwError(statusAndPW.pocStatus, s"failure when providing device info to goClient, errorCode: $code")
        }
      }
  }

  override def infoToCertifyAPI(poc: Poc, statusAndPW: StatusAndPW): Task[PocStatus] = {
    val status = statusAndPW.pocStatus
    if (status.certApiProvided) {
      Task(status)
    } else {
      val body = createCertifyApiBody(poc, statusAndPW)
      Task.fromFuture(certApiRequest(statusAndPW, body))
    }
  }

  protected def certApiRequest(statusAndPW: StatusAndPW, body: String): Future[PocStatus] =
    basicRequest
      .put(uri"$certifyApiURL")
      .body(body)
      .header(xAuthHeaderKey, certifyApiToken)
      .header(contentTypeHeaderKey, "application/json")
      .send()
      .map {
        _.code match {
          case Ok =>
            statusAndPW.pocStatus.copy(goClientProvided = true)
          case code =>
            throwError(statusAndPW.pocStatus, s"failure when providing device info to certifyAPI, errorCode: $code")
        }
      }

  private def createCertifyApiBody(
    poc: Poc,
    statusAndPW: StatusAndPW): String = {
    val deviceRequest = RegisterDeviceCertifyAPI(
      poc.pocName,
      poc.deviceId.toString,
      statusAndPW.devicePassword,
      poc.roleName,
      poc.roleName)
    write[RegisterDeviceCertifyAPI](deviceRequest)
  }

  def throwError(status: PocStatus, msg: String) =
    throw PocCreationError(status.copy(errorMessages = Some(msg)))

}
