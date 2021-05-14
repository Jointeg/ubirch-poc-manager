package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository, TenantRepository }
import com.ubirch.models.auth.CertIdentifier
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ APP, Both, Tenant }
import com.ubirch.services.poc.util.PKCS12Operations
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization

import java.util.UUID

trait PocCreator {

  def createPocs(): Task[PocCreationResult]
}

object PocCreator {
  @throws[PocCreationError]
  def throwAndLogError(pocAndStatus: PocAndStatus, msg: String, ex: Throwable, logger: Logger): Nothing = {
    logger.error(msg, ex)
    throwError(pocAndStatus, msg + ex.getMessage)
  }

  @throws[PocCreationError]
  def throwError(pocAndStatus: PocAndStatus, msg: String) =
    throw PocCreationError(pocAndStatus.copy(status = pocAndStatus.status.copy(errorMessage = Some(msg))), msg)
}

class PocCreatorImpl @Inject() (
  certHandler: CertHandler,
  deviceCreator: DeviceCreator,
  informationProvider: InformationProvider,
  keycloakHelper: KeycloakHelper,
  pocTable: PocRepository,
  pocStatusTable: PocStatusRepository,
  tenantTable: TenantRepository)(implicit formats: Formats)
  extends PocCreator
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  import deviceCreator._
  import informationProvider._
  import keycloakHelper._

  override def createPocs(): Task[PocCreationResult] = {
    pocTable.getAllUncompletedPocs().flatMap {
      case pocs if pocs.isEmpty =>
        logger.debug("no pocs waiting for completion")
        Task(PocCreationSuccess)
      case pocs =>
        logger.info(s"starting to create ${pocs.size} pocs")
        Task
          .gather(pocs.map(createPoc))
          .map(PocCreationMaybeSuccess)
    }
  }

  private def createPoc(poc: Poc): Task[Either[String, PocStatus]] = {
    retrieveStatusAndTenant(poc).flatMap {
      case (Some(status: PocStatus), Some(tenant: Tenant)) =>
        updateStatusOfPoc(poc, Processing)
          .flatMap(poc => process(PocAndStatus(poc, status), tenant))
      case (_, _) =>
        val errorMsg = s"cannot create poc with id ${poc.id} as tenant or status couldn't be found"
        logger.error(errorMsg)
        Task(Left(errorMsg))
    }.onErrorHandle { e =>
      val errorMsg =
        s"cannot create poc with id ${poc.id} as status and tenant couldn't be found. error: ${e.getMessage}"
      logger.error(errorMsg)
      Left(errorMsg)
    }
  }

  private def retrieveStatusAndTenant(poc: Poc): Task[(Option[PocStatus], Option[Tenant])] = {
    for {
      status <- pocStatusTable.getPocStatus(poc.id)
      tenant <- tenantTable.getTenant(poc.tenantId)
    } yield (status, tenant)
  }

  def doOrganisationUnitCertificateTasks(tenant: Tenant, pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.poc.clientCertRequired && tenant.usageType == APP) {
      throwPocCreationError(
        s"Could not create organisational unit certificate because Tenant usageType is set to $APP and clientCertRequires is set to true",
        pocAndStatus)
    } else if (tenant.usageType == APP || (pocAndStatus.poc.clientCertRequired && tenant.usageType == Both)) {
      createOrganisationalUnitCertificate(tenant, pocAndStatus)
    } else {
      Task(pocAndStatus)
    }
  }

  private def throwPocCreationError(msg: String, pocAndStatus: PocAndStatus) = {
    Task.raiseError(PocCreationError(
      pocAndStatus.copy(status = pocAndStatus.status.copy(errorMessage = Some(msg))),
      msg))
  }

  private def createOrganisationalUnitCertificate(tenant: Tenant, pocAndStatus: PocAndStatus) = {
    certHandler.createOrganisationalUnitCertificate(
      pocAndStatus.poc.tenantId.value.asJava(),
      pocAndStatus.poc.id,
      CertIdentifier.pocOrgUnitCert(tenant.tenantName, pocAndStatus.poc.pocName, pocAndStatus.poc.id)
    )
      .flatMap {
        case Left(certificationCreationError) =>
          Task(logger.error(certificationCreationError.msg)) >>
            throwPocCreationError(
              s"Could not create organisational unit certificate with orgUnitId: ${pocAndStatus.poc.id}",
              pocAndStatus)
        case Right(_) => Task(pocAndStatus.updateStatus(_.copy(orgUnitCertIdCreated = Some(true))))
      }
  }

  private def createSharedAuthCertificate(tenant: Tenant, pocAndStatus: PocAndStatus) = {
    val id = UUID.randomUUID()
    val certIdentifier = CertIdentifier.pocClientCert(tenant.tenantName, pocAndStatus.poc.pocName, id)

    for {
      result <- certHandler.createSharedAuthCertificate(pocAndStatus.poc.id, id, certIdentifier)
      statusWithResponse <- result match {
        case Left(certificationCreationError) =>
          Task(logger.error(certificationCreationError.msg)) >> throwPocCreationError(
            s"Could not create shared auth certificate with id: $id",
            pocAndStatus)
        case Right(sharedAuthResponse) => Task((
            pocAndStatus.updateStatus(_.copy(orgUnitCertIdCreated = Some(true))),
            sharedAuthResponse))
      }
      (pocAndStatus, sharedAuthResponse) = statusWithResponse
      _ <-
        Task.pure(
          PKCS12Operations.recreateFromBase16String(sharedAuthResponse.pkcs12, sharedAuthResponse.passphrase)).flatMap {
          case Left(_)         => throwPocCreationError("Certificate creation error", pocAndStatus)
          case Right(keystore) => Task(keystore)
        } // TODO: store the PKCS12 and passphrase in TeamDrive
    } yield pocAndStatus
  }

  private def doSharedAuthCertificateTasks(tenant: Tenant, pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.poc.clientCertRequired && tenant.usageType == APP) {
      throwPocCreationError(
        s"Could not create shared auth certificate because Tenant usageType is set to $APP and clientCertRequires is set to true",
        pocAndStatus)
    } else if (tenant.usageType == APP || (pocAndStatus.poc.clientCertRequired && tenant.usageType == Both)) {
      createSharedAuthCertificate(tenant, pocAndStatus)
    } else {
      Task(pocAndStatus)
    }
  }

//Todo: create and provide client certs
  //Todo: download and store logo
  private def process(pocAndStatus: PocAndStatus, tenant: Tenant): Task[Either[String, PocStatus]] = {
    logger.info(s"starting to create poc with id ${pocAndStatus.poc.id}")
    val creationResult = for {
      pocAndStatus1 <- doUserRealmRelatedTasks(pocAndStatus, tenant)
      pocAndStatus2 <- doDeviceRealmRelatedTasks(pocAndStatus1, tenant)
      pocAndStatus3 <- doOrganisationUnitCertificateTasks(tenant, pocAndStatus2)
      pocAndStatus4 <- doSharedAuthCertificateTasks(tenant, pocAndStatus3)
      statusAndPW1 <- createDevice(pocAndStatus4.poc, pocAndStatus4.status, tenant)
      statusAndPW2 <- infoToGoClient(pocAndStatus4.poc, statusAndPW1)
      completeStatus <- infoToCertifyAPI(pocAndStatus4.poc, statusAndPW2, tenant)
    } yield completeStatus

    (for {
      completePocAndStatus <- creationResult
      newPoc = completePocAndStatus.poc.copy(status = Completed)
      _ <- pocTable.updatePoc(newPoc)
      _ <- pocStatusTable.updatePocStatus(completePocAndStatus.status)
    } yield {
      Right(completePocAndStatus.status)
    }).onErrorHandleWith(handlePocCreationError)
  }

  private def updateStatusOfPoc(poc: Poc, newStatus: Status): Task[Poc] = {
    if (poc.status == newStatus) Task(poc)
    else {
      val updatedPoc = poc.copy(status = newStatus)
      pocTable
        .updatePoc(updatedPoc)
        .map(_ => updatedPoc)
    }
  }

  private def doDeviceRealmRelatedTasks(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    for {
      pocAndStatus1 <- createDeviceRole(pocAndStatus)
      pocAndStatus2 <- createDeviceGroup(pocAndStatus1, tenant)
      pocAndStatusFinal <- assignDeviceRealmRoleToGroup(pocAndStatus2, tenant)
    } yield pocAndStatusFinal
  }

  private def doUserRealmRelatedTasks(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    for {
      pocAndStatus1 <- createUserRole(pocAndStatus)
      pocAndStatus2 <- createUserGroup(pocAndStatus1, tenant)
      pocAndStatusFinal <- assignUserRoleToGroup(pocAndStatus2, tenant)
    } yield pocAndStatusFinal
  }

  private def handlePocCreationError[A](ex: Throwable): Task[Either[String, A]] = {
    ex match {
      case pce: PocCreationError =>
        (for {
          _ <- pocTable.updatePoc(pce.pocAndStatus.poc)
          _ <- pocStatusTable.updatePocStatus(pce.pocAndStatus.status)
        } yield {
          val msg = s"updated poc status after poc creation failed; ${pce.pocAndStatus.status}, error: ${pce.message}"
          logger.error(msg)
          Left(msg)
        }).onErrorHandle { ex =>
          val errorMsg = s"couldn't persist poc status after failed poc creation ${pce.pocAndStatus.status}"
          logger.error(errorMsg, ex)
          Left(errorMsg)
        }

      case ex: Throwable =>
        val errorMsg = "unexpected error during poc creation; "
        logger.error(errorMsg, ex)
        Task(Left(errorMsg + ex.getMessage))
    }
  }

}

case class StatusAndPW(pocStatus: PocStatus, devicePassword: String)

case class PocCreationError(pocAndStatus: PocAndStatus, message: String) extends Exception(message)

trait PocCreationResult
case object PocCreationFailure extends PocCreationResult
case object PocCreationSuccess extends PocCreationResult
case class PocCreationMaybeSuccess(list: Seq[Either[String, PocStatus]]) extends PocCreationResult
