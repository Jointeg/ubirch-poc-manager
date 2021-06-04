package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.PocConfig
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.execution.SttpResources
import com.ubirch.services.poc.PocCreator._
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import sttp.client.{ basicRequest, UriContext }
import sttp.model.StatusCode.{ Conflict, Ok }

trait InformationProvider {

  def infoToGoClient(poc: Poc, status: StatusAndPW): Task[StatusAndPW]

  def infoToCertifyAPI(poc: Poc, status: StatusAndPW, tenant: Tenant): Task[PocAndStatus]

}

case class RegisterDeviceGoClient(uuid: String, password: String)
case class RegisterDeviceCertifyAPI(
  name: String,
  pocType: String,
  endpoint: String,
  uuid: String,
  password: String,
  role: Option[String],
  location: Option[String],
  cert: Option[String],
  config: Option[String])

class InformationProviderImpl @Inject() (conf: Config, pocConfig: PocConfig, certHandler: CertHandler)(implicit
formats: Formats)
  extends InformationProvider
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global

  protected val goClientURL: String = conf.getString(ServicesConfPaths.GO_CLIENT_URL)
  private val goClientToken: String = conf.getString(ServicesConfPaths.GO_CLIENT_TOKEN)
  protected val certifyApiURL: String = conf.getString(ServicesConfPaths.CERTIFY_API_URL)
  private val certifyApiToken: String = conf.getString(ServicesConfPaths.CERTIFY_API_TOKEN)
  private val xAuthHeaderKey: String = "X-Auth-Token"
  private val contentTypeHeaderKey: String = "Content-Type"
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  @throws[PocCreationError]
  override def infoToGoClient(poc: Poc, statusAndPW: StatusAndPW): Task[StatusAndPW] = {
    val status = statusAndPW.status
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
    Task.deferFuture {
      val request = basicRequest
        .put(uri"$goClientURL")
        .header(xAuthHeaderKey, goClientToken)
        .header(contentTypeHeaderKey, "application/json")
        .body(body)
      SttpResources.backend.send(request)
        .map {
          _.code match {
            case Ok =>
              StatusAndPW(statusAndPW.status.copy(goClientProvided = true), statusAndPW.devicePassword)
            case Conflict =>
              StatusAndPW(statusAndPW.status.copy(goClientProvided = true), statusAndPW.devicePassword)
            case code =>
              throwError(
                PocAndStatus(poc, statusAndPW.status),
                s"failure when providing device info to goClient, statusCode: $code")
          }
        }
    }

  @throws[PocCreationError]
  override def infoToCertifyAPI(poc: Poc, statusAndPW: StatusAndPW, tenant: Tenant): Task[PocAndStatus] = {
    val status = statusAndPW.status
    if (status.certifyApiProvided) {
      Task(PocAndStatus(poc, status))
    } else {
      for {
        body <- getCertifyApiBody(poc, statusAndPW, tenant)
        response <- certifyApiRequest(poc, statusAndPW, body).map(newStatus => PocAndStatus(poc, newStatus))
          .onErrorHandle(ex =>
            throwAndLogError(
              PocAndStatus(poc, status),
              "an error occurred when providing info to certify api; ",
              ex,
              logger))
      } yield response
    }
  }

  @throws[PocCreationError]
  protected def certifyApiRequest(poc: Poc, statusAndPW: StatusAndPW, body: String): Task[PocStatus] =
    Task.deferFuture {
      val request = basicRequest
        .post(uri"$certifyApiURL")
        .header(xAuthHeaderKey, certifyApiToken)
        .header(contentTypeHeaderKey, "application/json")
        .body(body)
      SttpResources.backend.send(request)
        .map {
          _.code match {
            case Ok =>
              statusAndPW.status.copy(certifyApiProvided = true)
            case code =>
              throwError(
                PocAndStatus(poc, statusAndPW.status),
                s"failure when providing device info to certifyAPI, errorCode: $code")
          }
        }
    }

  private[poc] def getCertifyApiBody(poc: Poc, sAndPW: StatusAndPW, tenant: Tenant): Task[String] = {

    def getSharedAuthCertIdOrThrowError = Task(poc.sharedAuthCertId.getOrElse(throwError(
      PocAndStatus(poc, sAndPW.status),
      "Tried to obtain shared auth cert ID from PoC but it was not defined")))

    def getSharedAuthCertFromResponse(maybeCert: Either[CertificateCreationError, String]): Task[Option[String]] = {
      maybeCert match {
        case Left(error) =>
          Task(throwAndLogError(
            PocAndStatus(poc, sAndPW.status),
            "Requested CertManager for shared auth cert but it responded with error ",
            error,
            logger))
        case Right(value) => Task(Some(value))
      }
    }

    def clientCert: Task[Option[String]] =
      if (poc.clientCertRequired) {
        for {
          certId <- getSharedAuthCertIdOrThrowError
          maybeCert <- certHandler.getCert(certId)
          cert <- getSharedAuthCertFromResponse(maybeCert)
        } yield cert
      } else if (tenant.sharedAuthCert.isEmpty) {
        Task(throwError(
          PocAndStatus(poc, sAndPW.status),
          "failed to create certifyAPI body; client cert of tenant is missing"))
      } else {
        Task(tenant.sharedAuthCert.map(_.value))
      }

    val endpoint = pocConfig.pocTypeEndpointMap.getOrElse(
      poc.pocType,
      throwError(
        PocAndStatus(poc, sAndPW.status),
        s"couldn't find matching endpoint in pocTypeEndpointMap for pocType ${poc.pocType}"))

    clientCert.map(cert => {
      val registerDevice =
        RegisterDeviceCertifyAPI(
          poc.pocName,
          poc.pocType,
          endpoint,
          poc.getDeviceId,
          sAndPW.devicePassword,
          if (pocConfig.roleNeeded.contains(poc.pocType)) Some(poc.roleName) else None,
          if (pocConfig.locationNeeded.contains(poc.pocType)) Some(poc.externalId) else None,
          cert,
          poc.extraConfig.map(jsonConfig => write(jsonConfig.jvalue))
        )
      write[RegisterDeviceCertifyAPI](registerDevice)
    })
  }

  private def getGoClientBody(poc: Poc, statusAndPW: StatusAndPW): String = {
    val registerDevice = RegisterDeviceGoClient(poc.getDeviceId, statusAndPW.devicePassword)
    write[RegisterDeviceGoClient](registerDevice)
  }

}
