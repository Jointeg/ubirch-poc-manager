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

  def toGoClient(poc: Poc, status: PocStatus): Task[PocStatus]

  def toCertifyAPI(poc: Poc, status: PocStatus): Task[PocStatus]

}

case class RegisterDevice(deviceId: UUID, password: String)

class InformationProviderImpl @Inject() (conf: Config)(implicit formats: Formats) extends InformationProvider {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  private val goClientURL: String = conf.getString(ServicesConfPaths.GO_CLIENT_URL)
  private val goClientToken: String = conf.getString(ServicesConfPaths.GO_CLIENT_TOKEN)
  private val certifyApiURL: String = conf.getString(ServicesConfPaths.CERTIFY_API_URL)
  private val certifyApiToken: String = conf.getString(ServicesConfPaths.CERTIFY_API_TOKEN)
  private val xAuthHeader: String = "X-Auth-Token"
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  override def toGoClient(poc: Poc, status: PocStatus): Task[PocStatus] = {
    val body = RegisterDevice(poc.deviceId, "password")
    val response =
      basicRequest
        .put(uri"$goClientURL")
        .body(write[RegisterDevice](body))
        .header(xAuthHeader, goClientToken)
        .send()
        .map {
          _.code match {
            case Ok =>
              status.copy(goClientProvided = true)
            case code =>
              throwError(status, s"failure when providing device info to goClient, errorCode: $code")
          }
        }
    Task.fromFuture(response)
  }

  override def toCertifyAPI(poc: Poc, status: PocStatus): Task[PocStatus] = ???

  def throwError(status: PocStatus, msg: String) =
    throw PocCreationError(status.copy(errorMessages = Some(msg)))

}
