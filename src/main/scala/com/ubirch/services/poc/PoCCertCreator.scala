package com.ubirch.services.poc
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.PKCS12Operations
import com.ubirch.services.teamdrive.TeamDriveService
import com.ubirch.services.teamdrive.model.SpaceName
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
    val certIdentifier = CertIdentifier.pocClientCert(poc.pocName, id)

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
      (newPocAndStatus, sharedAuthResponse) = statusWithResponse
      _ <-
        Task(
          PKCS12Operations.recreateFromBase16String(sharedAuthResponse.pkcs12, sharedAuthResponse.passphrase)).flatMap {
          case Left(_)         => pocCreationError("Certificate creation error", newPocAndStatus)
          case Right(keystore) => Task(keystore)
        }
      name = SpaceName.ofPoc(stage, tenant, poc)
      _ <- teamDriveService.shareCert(
        name,
        ubirchAdmins,
        sharedAuthResponse.passphrase,
        sharedAuthResponse.pkcs12
      ).onErrorHandleWith {
        ex =>
          logger.error(
            s"Could not upload shared auth certificate in TeamDrive name: $name id: $id, pocIc: ${poc.id.toString}",
            ex)
          pocCreationError(s"Could not upload shared auth certificate in TeamDrive with name: $name", newPocAndStatus)
      }
    } yield {
      newPocAndStatus
        .updatePoc(_.copy(sharedAuthCertId = Some(sharedAuthResponse.certUuid)))
        .updateStatus(_.copy(clientCertProvided = Some(true)))
    }
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
        CertIdentifier.pocOrgUnitCert(pocAndStatus.poc.pocName, pocAndStatus.poc.id)
      ).flatMap {
        case Left(certificationCreationError) =>
          Task(logger.error(certificationCreationError.msg)) >>
            pocCreationError(
              s"Could not create organisational unit certificate with orgUnitId: ${pocAndStatus.poc.id}",
              pocAndStatus)
        case Right(_) => Task(pocAndStatus.updateStatus(_.copy(orgUnitCertCreated = Some(true))))
      }
}
