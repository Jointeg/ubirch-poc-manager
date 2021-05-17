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
import sttp.client.json4s.asJson
import sttp.model.StatusCode

import java.util.UUID
import javax.inject.Inject

trait CertHandler {

  def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]]

  def createSharedAuthCertificate(
    orgUnitId: UUID,
    groupId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, SharedAuthCertificateResponse]]
}

class CertCreatorImpl @Inject() (conf: Config)(implicit formats: Formats) extends CertHandler with LazyLogging {

  implicit val backend = SttpResources.backend
  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global

  private val certManagerUrl: String = conf.getString(ServicesConfPaths.CERT_MANAGER_URL)
  private val certManagerToken: String = conf.getString(ServicesConfPaths.CERT_MANAGER_TOKEN)
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  override def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]] = {
    val response = Task.deferFuture(basicRequest
      .post(uri"$certManagerUrl/orgs/${orgUUID.toString}/units/${orgUnitId.toString}")
      .body(write[CreateOrganisationalUnitCertRequest](CreateOrganisationalUnitCertRequest(identifier.value)))
      .auth.bearer(certManagerToken)
      .response(ignore)
      .send())

    response.flatMap(response =>
      if (response.code == StatusCode.Created)
        Task(Right(()))
      else
        Task(Left(CertificateCreationError(
          s"Could not create organisational unit certificate with orgUnitId: $orgUnitId because received ${response.code} from CertManager"))))

  }

  override def createSharedAuthCertificate(
    orgUnitId: UUID,
    groupId: UUID,
    identifier: CertIdentifier): Task[Either[
    CertificateCreationError,
    SharedAuthCertificateResponse]] = {
    val response = Task.deferFuture(basicRequest
      .post(uri"$certManagerUrl/units/${orgUnitId.toString}/groups/${groupId.toString}")
      .body(write[CreateSharedAuthCertificateRequest](CreateSharedAuthCertificateRequest(identifier.value)))
      .auth.bearer(certManagerToken)
      .response(asJson[SharedAuthCertificateResponse])
      .send())

    import cats.syntax.either._

    response.map(_.body.leftMap(responseError => CertificateCreationError(responseError.getMessage)))
  }
}

case class CreateOrganisationalUnitCertRequest(identifier: String)
case class CertificateCreationError(msg: String) extends Throwable
case class CreateSharedAuthCertificateRequest(identifier: String)
