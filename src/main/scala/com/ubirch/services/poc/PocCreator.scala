package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.ConfPaths.TeamDrivePaths
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository, TenantRepository }
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ API, Tenant }
import com.ubirch.services.teamdrive.TeamDriveService
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization

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
  deviceHelper: DeviceHelper,
  informationProvider: InformationProvider,
  keycloakHelper: KeycloakHelper,
  pocTable: PocRepository,
  pocStatusTable: PocStatusRepository,
  tenantTable: TenantRepository,
  teamDriveService: TeamDriveService,
  config: Config)(implicit formats: Formats)
  extends PocCreator
  with LazyLogging {

  private val ubirchAdminsEmails: Seq[String] =
    config.getString(TeamDrivePaths.UBIRCH_ADMINS).trim.split(",").map(_.trim)
  private val stage: String = config.getString(TeamDrivePaths.STAGE)

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  import deviceCreator._
  import deviceHelper._
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
    if (pocAndStatus.poc.clientCertRequired && tenant.usageType == API)
      PoCCertCreator.pocCreationError("a poc shouldn't require client cert if tenant usageType is API", pocAndStatus)
    else if (pocAndStatus.poc.clientCertRequired && !pocAndStatus.status.orgUnitCertCreated.contains(true)) {
      PoCCertCreator.createPoCOrganisationalUnitCertificate(tenant, pocAndStatus)(certHandler)
    } else {
      Task(pocAndStatus)
    }
  }

  private def doSharedAuthCertificateTasks(tenant: Tenant, pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.poc.clientCertRequired && tenant.usageType == API)
      PoCCertCreator.pocCreationError(
        "a poc shouldn't require shared auth cert if tenant usageType is API",
        pocAndStatus)
    else if (pocAndStatus.poc.clientCertRequired && !pocAndStatus.status.clientCertCreated.contains(true)) {
      PoCCertCreator.createPoCSharedAuthCertificate(tenant, pocAndStatus, ubirchAdminsEmails, stage)(
        certHandler,
        teamDriveService)
    } else {
      Task(pocAndStatus)
    }
  }

  //Todo: create and provide client certs
  //Todo: download and store logo
  private def process(pocAndStatus: PocAndStatus, tenant: Tenant): Task[Either[String, PocStatus]] = {
    logger.info(s"starting to process poc with id ${pocAndStatus.poc.id}")
    val creationResult = for {
      pocAndStatus1 <- doCertifyRealmRelatedTasks(pocAndStatus, tenant)
      pocAndStatus2 <- doDeviceRealmRelatedTasks(pocAndStatus1, tenant)
      statusAndPW3 <- createDevice(pocAndStatus2.poc, pocAndStatus2.status, tenant)
      status4 <- addGroupsToDevice(pocAndStatus2.poc, statusAndPW3.pocStatus)
      pocAndStatus3 <- doOrganisationUnitCertificateTasks(tenant, PocAndStatus(pocAndStatus2.poc, status4))
      pocAndStatus4 <- doSharedAuthCertificateTasks(tenant, pocAndStatus3)
      statusAndPW5 <- infoToGoClient(pocAndStatus4.poc, statusAndPW3.copy(pocStatus = pocAndStatus4.status))
      completeStatus <- infoToCertifyAPI(pocAndStatus4.poc, statusAndPW5, tenant)
    } yield completeStatus

    (for {
      completePocAndStatus <- creationResult
      newPoc = completePocAndStatus.poc.copy(status = Completed)
      _ <- pocTable.updatePoc(newPoc)
      _ <- pocStatusTable.updatePocStatus(completePocAndStatus.status.copy(errorMessage = None))
    } yield {
      logger.info(s"finished to create poc with id ${pocAndStatus.poc.id}")
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

  private def doCertifyRealmRelatedTasks(pocAndStatus: PocAndStatus, tenant: Tenant): Task[PocAndStatus] = {
    for {
      pocAndStatus1 <- createCertifyRole(pocAndStatus)
      pocAndStatus2 <- createCertifyGroup(pocAndStatus1, tenant)
      pocAndStatus3 <- assignCertifyRoleToGroup(pocAndStatus2, tenant)
      pocAndStatus4 <- createAdminGroup(pocAndStatus3)
      pocAndStatus5 <- assignAdminRole(pocAndStatus4)
      pocAndStatus6 <- createEmployeeGroup(pocAndStatus5)
      pocAndStatusFinal <- assignEmployeeRole(pocAndStatus6)
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
