package com.ubirch.services.poc

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.services.execution.SttpResources
import monix.eval.Task
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import sttp.client._
import sttp.client.json4s.asJson
import sttp.model.StatusCode

import java.util.UUID
import javax.inject.Inject

case class SharedAuthCertificateResponse(passphrase: String, pkcs12: String)

trait CertHandler {

  def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]]

  def createSharedAuthCertificate(
    orgUnitId: UUID,
    groupId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, SharedAuthCertificateResponse]]

  def createCert(poc: Poc, status: PocStatus): Task[PocStatus]

  def provideCert(poc: Poc, status: PocStatus): Task[PocStatus]
}

class CertCreatorImpl @Inject() (conf: Config)(implicit formats: Formats) extends CertHandler with LazyLogging {

  private val certManagerUrl: String = conf.getString(ServicesConfPaths.CERT_MANAGER_URL)
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  override def createCert(poc: Poc, status: PocStatus): Task[PocStatus] = {
    status.clientCertCreated match {
      case Some(false) =>
        Task(status)
      // create client cert, update state and return
      case _ => Task(status)
    }
  }

  override def provideCert(poc: Poc, status: PocStatus): Task[PocStatus] = {
    status.clientCertProvided match {
      case Some(false) =>
        Task(status)
      // provide client cert, update state and return
      case _ => Task(status)
    }
  }
  override def createOrganisationalUnitCertificate(
    orgUUID: UUID,
    orgUnitId: UUID,
    identifier: CertIdentifier): Task[Either[CertificateCreationError, Unit]] = {
    SttpResources.monixBackend.flatMap { implicit backend =>
      val response = basicRequest
        .post(uri"$certManagerUrl/orgs/${orgUUID.toString}/units/${orgUnitId.toString}")
        .body(write[CreateOrganisationalUnitCertRequest](CreateOrganisationalUnitCertRequest(identifier.value)))
        .response(ignore)
        .send()

      response.flatMap(response =>
        if (response.code == StatusCode.Created)
          Task(Right(()))
        else
          Task(Left(CertificateCreationError(
            s"Could not create organisational unit certificate with orgUnitId: $orgUnitId because received ${response.code} from CertManager"))))
    }

  }

  override def createSharedAuthCertificate(
    orgUnitId: UUID,
    groupId: UUID,
    identifier: CertIdentifier): Task[Either[
    CertificateCreationError,
    SharedAuthCertificateResponse]] = {
    SttpResources.monixBackend.flatMap { implicit backend =>
      val response = basicRequest
        .post(uri"$certManagerUrl/units/${orgUnitId.toString}/groups/${groupId.toString}")
        .body(write[CreateSharedAuthCertificateRequest](CreateSharedAuthCertificateRequest(identifier.value)))
        .response(asJson[SharedAuthCertificateResponse])
        .send()

      import cats.syntax.either._

      response.map(_.body.leftMap(responseError => CertificateCreationError(responseError.getMessage)))
    }
  }
}

case class CreateOrganisationalUnitCertRequest(identifier: String)
case class CertificateCreationError(msg: String) extends Throwable
case class CreateSharedAuthCertificateRequest(identifier: String)
