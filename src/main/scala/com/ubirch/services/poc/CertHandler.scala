package com.ubirch.services.poc

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.auth.cert.SharedAuthCertificateResponse
import com.ubirch.services.execution.SttpResources
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.json4s.asJson
import sttp.model.StatusCode

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

trait CertHandler {

  def createOrganisationalCertificate(
    orgUUID: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]]

  def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]]

  def createSharedAuthCertificate(
    orgUnitId: UUID,
    groupId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, SharedAuthCertificateResponse]]

  def getCert(certId: UUID): Task[Either[CertificateCreationError, String]]

}

class CertCreatorImpl @Inject() (conf: Config)(implicit formats: Formats) extends CertHandler with LazyLogging {

  implicit val backend: SttpBackend[Future, Nothing, WebSocketHandler] = SttpResources.backend
  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global

  private val certManagerUrl: String = conf.getString(ServicesConfPaths.CERT_MANAGER_URL)
  private val certManagerToken: String = conf.getString(ServicesConfPaths.CERT_MANAGER_TOKEN)
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  def createOrganisationalCertificate(
    orgUUID: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]] =
    Task.deferFuture(
      basicRequest
        .post(uri"$certManagerUrl/orgs/${orgUUID.toString}")
        .body(write[CreateOrganisationalCertRequest](CreateOrganisationalCertRequest(identifier.value)))
        .auth.bearer(certManagerToken)
        .header("Content-Type", "application/json")
        .response(ignore)
        .send())
      .map(response =>
        if (response.code == StatusCode.Created || response.code == StatusCode.Conflict)
          Right(())
        else
          Left(CertificateCreationError(
            s"Could not create organisational certificate with orgId: $orgUUID because received ${response.code} from CertManager")))
      .onErrorHandle(handleException(_, s"creation of org cert failed for tenant $orgUUID; "))

  override def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]] = {
    val response = Task.deferFuture(basicRequest
      .post(uri"$certManagerUrl/orgs/${orgUUID.toString}/units/${orgUnitId.toString}")
      .body(write[CreateOrganisationalUnitCertRequest](CreateOrganisationalUnitCertRequest(identifier.value)))
      .header("Content-Type", "application/json")
      .auth.bearer(certManagerToken)
      .response(ignore)
      .send())

    response.flatMap(response =>
      if (response.code == StatusCode.Created || response.code == StatusCode.Conflict)
        Task(Right(()))
      else
        Task(Left(CertificateCreationError(
          s"Could not create organisational unit certificate with orgUnitId: $orgUnitId because received ${response.code} from CertManager"))))

  }

  override def createSharedAuthCertificate(
    orgUnitId: UUID,
    groupId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, SharedAuthCertificateResponse]] = {

    val response = Task.deferFuture(basicRequest
      .post(uri"$certManagerUrl/units/${orgUnitId.toString}/groups/${groupId.toString}")
      .body(write[CreateSharedAuthCertificateRequest](CreateSharedAuthCertificateRequest(identifier.value)))
      .header("Content-Type", "application/json")
      .auth.bearer(certManagerToken)
      .response(asJson[SharedAuthCertificateResponse])
      .send())

    response.flatMap { r =>
      if (r.code == StatusCode.Conflict) logger.debug("response object also has code in case of conflict")
      r.body match {
        case Right(response: SharedAuthCertificateResponse) => Task(Right(response))

        case Left(ex: HttpError) if ex.statusCode == sttp.model.StatusCode.Conflict =>
          logger.info("shared auth creation for responded with conflict; will be updated instead")
          updateSharedAuthCertificate(groupId)

        case Left(responseError: ResponseError[Exception]) =>
          logger.error("unexpected exception during shared auth cert creation", responseError)
          Task(Left(CertificateCreationError(responseError.getMessage)))
      }
    }
  }

  private def updateSharedAuthCertificate(groupId: UUID)
    : Task[Either[CertificateCreationError, SharedAuthCertificateResponse]] = {

    val response = Task.deferFuture(basicRequest
      .put(uri"$certManagerUrl/groups/${groupId.toString}")
      .auth.bearer(certManagerToken)
      .response(asJson[SharedAuthCertificateResponse])
      .send())
    import cats.syntax.either._
    response.map(_.body.leftMap(responseError => CertificateCreationError(responseError.getMessage)))
  }

  def getCert(certId: UUID): Task[Either[CertificateCreationError, String]] =
    Task.deferFuture {
      basicRequest
        .get(uri"$certManagerUrl/certs/$certId")
        .auth.bearer(certManagerToken)
        .response(asString)
        .send()
        .map(_.body.fold(errorMsg => Left(CertificateCreationError(errorMsg)), cert => Right(cert)))
    }.onErrorHandle(handleException(_, s"GET cert/$certId failed; "))

  private def handleException(ex: Throwable, errorMsg: String): Left[CertificateCreationError, Nothing] = {
    logger.error(errorMsg, ex)
    Left(CertificateCreationError(errorMsg + ex.getMessage))
  }
}

case class CreateOrganisationalCertRequest(identifier: String)
case class CreateOrganisationalUnitCertRequest(identifier: String)
case class CertificateCreationError(msg: String) extends Throwable(msg)
case class CreateSharedAuthCertificateRequest(identifier: String)
