package com.ubirch.services.poc
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.auth.cert.SharedAuthCertificateResponse
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.PKCS12Operations
import com.ubirch.services.teamdrive.TeamDriveService
import monix.eval.Task

import java.util.UUID

object PoCCertCreator extends LazyLogging {

  def createPoCSharedAuthCertificate(
    tenant: Tenant,
    pocAndStatus: PocAndStatus,
    ubirchAdmins: Seq[String],
    stage: String)(certHandler: CertHandler, teamDriveService: TeamDriveService): Task[PocAndStatus] = {
    val id = UUID.randomUUID()
    val poc = pocAndStatus.poc
    val certIdentifier = CertIdentifier.pocClientCert(tenant.tenantName, poc.pocName, id)

    for {
      result <- certHandler.createSharedAuthCertificate(poc.id, id, certIdentifier)
      statusWithResponse <- result match {
        case Left(certificationCreationError) =>
          Task(logger.error(certificationCreationError.msg)) >> pocCreationError(
            s"Could not create shared auth certificate with id: $id",
            pocAndStatus)
        case Right(sharedAuthResponse) => Task((
            pocAndStatus.updateStatus(_.copy(clientCertCreated = Some(true))),
            sharedAuthResponse))
      }
      (pocAndStatus, sharedAuthResponse) = statusWithResponse
      _ <-
        Task.pure(
          PKCS12Operations.recreateFromBase16String(sharedAuthResponse.pkcs12, sharedAuthResponse.passphrase)).flatMap {
          case Left(_)         => pocCreationError("Certificate creation error", pocAndStatus)
          case Right(keystore) => Task(keystore)
        } // TODO: store the PKCS12 and passphrase in TeamDrive
      name = s"${stage}_tenantName_${poc.pocName}_${poc.externalId}" // TODO missing tenantName
      _ <- teamDriveService.shareCert(
        name,
        ubirchAdmins :+ poc.manager.managerEmail,
        sharedAuthResponse.passphrase,
        sharedAuthResponse.pkcs12
      )
    } yield pocAndStatus.updatePoc(_.copy(sharedAuthCertId = Some(sharedAuthResponse.certUuid)))
  }

  def pocCreationError[A](msg: String, pocAndStatus: PocAndStatus): Task[A] = {
    Task.raiseError(PocCreationError(
      pocAndStatus.copy(status = pocAndStatus.status.copy(errorMessage = Some(msg))),
      msg))
  }

  def createPoCOrganisationalUnitCertificate(
    tenant: Tenant,
    pocAndStatus: PocAndStatus)(certHandler: CertHandler): Task[PocAndStatus] =
    certHandler
      .createOrganisationalUnitCertificate(
        pocAndStatus.poc.tenantId.value.asJava(),
        pocAndStatus.poc.id,
        CertIdentifier.pocOrgUnitCert(tenant.tenantName, pocAndStatus.poc.pocName, pocAndStatus.poc.id)
      ).flatMap {
        case Left(certificationCreationError) =>
          Task(logger.error(certificationCreationError.msg)) >>
            pocCreationError(
              s"Could not create organisational unit certificate with orgUnitId: ${pocAndStatus.poc.id}",
              pocAndStatus)
        case Right(_) => Task(pocAndStatus.updateStatus(_.copy(orgUnitCertCreated = Some(true))))
      }
}
