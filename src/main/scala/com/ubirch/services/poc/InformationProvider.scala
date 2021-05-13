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
import sttp.client.{ basicRequest, UriContext }
import sttp.model.StatusCode.{ Conflict, Ok }
import PocCreator._
import com.ubirch.models.tenant.Tenant
import sttp.client.asynchttpclient.monix.AsyncHttpClientMonixBackend

trait InformationProvider {

  def infoToGoClient(poc: Poc, status: StatusAndPW): Task[StatusAndPW]

  def infoToCertifyAPI(poc: Poc, status: StatusAndPW, tenant: Tenant): Task[PocAndStatus]

}

case class RegisterDeviceGoClient(uuid: String, password: String)
case class RegisterDeviceCertifyAPI(
  name: String,
  deviceId: String,
  password: String,
  role: Option[String],
  cert: Option[String])

class InformationProviderImpl @Inject() (conf: Config)(implicit formats: Formats)
  extends InformationProvider
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  private val monixBackend = AsyncHttpClientMonixBackend()
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
      goClientRequest(poc, statusAndPW, body)
        .onErrorHandle(ex =>
          throwAndLogError(
            PocAndStatus(poc, status),
            "an error occurred when providing info to go client; ",
            ex,
            logger))
    }
  }

  @throws[PocCreationError]
  protected def goClientRequest(poc: Poc, statusAndPW: StatusAndPW, body: String): Task[StatusAndPW] =
    monixBackend.flatMap { backend =>
      val request = basicRequest
        .put(uri"$goClientURL")
        .header(xAuthHeaderKey, goClientToken)
        .header(contentTypeHeaderKey, "application/json")
        .body(body)
      backend.send(request).map {
        _.code match {
          case Ok =>
            StatusAndPW(statusAndPW.pocStatus.copy(goClientProvided = true), statusAndPW.devicePassword)
          case Conflict =>
            StatusAndPW(statusAndPW.pocStatus.copy(goClientProvided = true), statusAndPW.devicePassword)
          case code =>
            throwError(
              PocAndStatus(poc, statusAndPW.pocStatus),
              s"failure when providing device info to goClient, statusCode: $code")
        }
      }
    }

  @throws[PocCreationError]
  override def infoToCertifyAPI(poc: Poc, statusAndPW: StatusAndPW, tenant: Tenant): Task[PocAndStatus] = {
    val status = statusAndPW.pocStatus
    if (status.certifyApiProvided) {
      Task(PocAndStatus(poc, status))
    } else {
      val body = getCertifyApiBody(poc, statusAndPW, tenant)
      certifyApiRequest(poc, statusAndPW, body).map(newStatus => PocAndStatus(poc, newStatus))
        .onErrorHandle(ex =>
          throwAndLogError(
            PocAndStatus(poc, status),
            "an error occurred when providing info to certify api; ",
            ex,
            logger))
    }
  }

  @throws[PocCreationError]
  protected def certifyApiRequest(poc: Poc, statusAndPW: StatusAndPW, body: String): Task[PocStatus] =
    monixBackend.flatMap { backend =>
      val request = basicRequest
        .post(uri"$certifyApiURL")
        .body(body)
        .header(xAuthHeaderKey, certifyApiToken)
        .header(contentTypeHeaderKey, "application/json")
      backend.send(request)
        .map {
          _.code match {
            case Ok =>
              statusAndPW.pocStatus.copy(certifyApiProvided = true)
            case code =>
              throwError(
                PocAndStatus(poc, statusAndPW.pocStatus),
                s"failure when providing device info to certifyAPI, errorCode: $code")
          }
        }
    }

  private def getCertifyApiBody(poc: Poc, statusAndPW: StatusAndPW, tenant: Tenant): String = {

    val clientCert = if (poc.clientCertRequired) tenant.clientCert.map(_.value.value) else None

    val registerDevice =
      RegisterDeviceCertifyAPI(
        poc.pocName,
        poc.getDeviceId,
        statusAndPW.devicePassword,
        Some(poc.roleName),
        clientCert)
    write[RegisterDeviceCertifyAPI](registerDevice)
  }

  private def getGoClientBody(poc: Poc, statusAndPW: StatusAndPW): String = {
    val registerDevice = RegisterDeviceGoClient(poc.getDeviceId, statusAndPW.devicePassword)
    write[RegisterDeviceGoClient](registerDevice)
  }
}
