package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.poc.{ Poc, PocStatus, StatusAndDeviceInfo }
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

  def toGoClient(poc: Poc, status: StatusAndDeviceInfo): Task[StatusAndDeviceInfo]

  def toCertifyAPI(poc: Poc, status: StatusAndDeviceInfo): Task[PocStatus]

}

case class RegisterDeviceGoClient(deviceId: UUID, password: String)
case class RegisterDeviceCertifyAPI(name: String, deviceId: String, password: String, role: String, cert: String)

class InformationProviderImpl @Inject() (conf: Config)(implicit formats: Formats) extends InformationProvider {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  private val goClientURL: String = conf.getString(ServicesConfPaths.GO_CLIENT_URL)
  private val goClientToken: String = conf.getString(ServicesConfPaths.GO_CLIENT_TOKEN)
  private val certifyApiURL: String = conf.getString(ServicesConfPaths.CERTIFY_API_URL)
  private val certifyApiToken: String = conf.getString(ServicesConfPaths.CERTIFY_API_TOKEN)
  private val xAuthHeaderKey: String = "X-Auth-Token"
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  override def toGoClient(poc: Poc, statusAndPW: StatusAndDeviceInfo): Task[StatusAndDeviceInfo] = {

    val body = RegisterDeviceGoClient(poc.deviceId, statusAndPW.devicePassword.toString)
    val r = basicRequest
      .put(uri"$goClientURL")
      .body(write[RegisterDeviceGoClient](body))
      .header(xAuthHeaderKey, goClientToken)
      .send()
      .map {
        _.code match {
          case Ok =>
            StatusAndDeviceInfo(statusAndPW.pocStatus.copy(goClientProvided = true), statusAndPW.devicePassword)
          case code =>
            throwError(statusAndPW.pocStatus, s"failure when providing device info to goClient, errorCode: $code")
        }
      }
    Task.fromFuture(r)
  }

  override def toCertifyAPI(poc: Poc, statusAndPW: StatusAndDeviceInfo): Task[PocStatus] = {
    val body = createCertifyApiBody(poc, statusAndPW)

    val r = basicRequest
      .put(uri"$certifyApiURL")
      .body(write[RegisterDeviceCertifyAPI](body))
      .header(xAuthHeaderKey, certifyApiToken)
      .send()
      .map {
        _.code match {
          case Ok =>
            statusAndPW.pocStatus.copy(goClientProvided = true)
          case code =>
            throwError(statusAndPW.pocStatus, s"failure when providing device info to certifyAPI, errorCode: $code")
        }
      }
    Task.fromFuture(r)
  }

  private def createCertifyApiBody(
    poc: Poc,
    statusAndPW: StatusAndDeviceInfo) = {
    RegisterDeviceCertifyAPI(
      poc.pocName,
      poc.deviceId.toString,
      statusAndPW.devicePassword.toString,
      poc.roleName,
      poc.roleName)
  }
  def throwError(status: PocStatus, msg: String) =
    throw PocCreationError(status.copy(errorMessages = Some(msg)))

}
