package com.ubirch.services.poc

import cats.data.EitherT
import com.google.inject.Inject
import com.typesafe.config.Config
import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.ConfPaths.TeamDrivePaths
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocLogoRepository, PocRepository, PocStatusRepository, TenantRepository }
import com.ubirch.models.common.{ ProcessingElements, WaitingForNewElements }
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ API, Tenant }
import com.ubirch.services.poc.util.ImageLoader
import com.ubirch.services.teamdrive.TeamDriveService
import com.ubirch.util.PocAuditLogging
import monix.eval.Task
import monix.execution.Scheduler
import org.joda.time.DateTime
import org.json4s.Formats
import org.json4s.native.Serialization

trait PocCreator {

  def createPocs(): Task[Unit]
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
  quillMonixJdbcContext: QuillMonixJdbcContext,
  imageLoader: ImageLoader,
  pocLogoRepository: PocLogoRepository,
  config: Config)(implicit formats: Formats)
  extends PocCreator
  with LazyLogging
  with PocAuditLogging {

  private val ubirchAdminsEmails: Seq[String] =
    config.getString(TeamDrivePaths.UBIRCH_ADMINS).trim.split(",").map(_.trim)
  private val stage: String = config.getString(TeamDrivePaths.STAGE)

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  import deviceCreator._
  import deviceHelper._
  import informationProvider._
  import keycloakHelper._

  override def createPocs(): Task[Unit] = {
    pocTable.getAllUncompletedPocsIds().flatMap {
      case pocIds if pocIds.isEmpty =>
        logger.debug("no pocs waiting for completion")
        Task(PocCreationLoop.loopState.set(WaitingForNewElements(DateTime.now(), "PoC"))).void
      case pocIds =>
        Task.sequence(pocIds.map(pocId => {
          (for {
            _ <- Task.cancelBoundary
            _ <- Task(PocCreationLoop.loopState.set(ProcessingElements(DateTime.now(), "PoC", pocId.toString)))
            poc <- pocTable.unsafeGetUncompletedPocByIds(pocId)
            _ <- createPoc(poc).uncancelable
          } yield ()).onErrorHandle(ex => {
            logger.error(s"Unexpected error has happened while creating PoC with id $pocId", ex)
            ()
          })
        })).void
    }
  }

  private def incrementCreationAttemptCounter(poc: Poc) = {
    if (poc.creationAttempts >= 10) {
      Task(logger.warn(
        s"PoC with ID ${poc.id} has exceeded the maximum creation attempts number. Changing its status to Aborted.")) >>
        pocTable.incrementCreationAttempt(poc.id) >> pocTable.updatePoc(poc.copy(status = Aborted))
    } else {
      pocTable.incrementCreationAttempt(poc.id)
    }
  }

  private def createPoc(poc: Poc): Task[Either[String, PocStatus]] = {
    import cats.syntax.all._

    retrieveStatusAndTenant(poc).flatMap {
      case (Some(status: PocStatus), Some(tenant: Tenant)) =>
        for {
          updatedPoc <- updateStatusOfPoc(poc, Processing)
          result <- process(PocAndStatus(updatedPoc, status.copy(errorMessage = None)), tenant)
          _ <- result.leftTraverse(_ => incrementCreationAttemptCounter(updatedPoc))
        } yield result
      case (_, _) =>
        val errorMsg = s"cannot create poc with id ${poc.id} as tenant or status couldn't be found"
        logger.error(errorMsg)
        incrementCreationAttemptCounter(poc) >> Task(Left(errorMsg))
    }.onErrorHandleWith { e =>
      val errorMsg =
        s"cannot create poc with id ${poc.id} as status and tenant couldn't be found. error: ${e.getMessage}"
      logger.error(errorMsg)
      incrementCreationAttemptCounter(poc) >> Task(Left(errorMsg))
    }
  }

  private def retrieveStatusAndTenant(poc: Poc): Task[(Option[PocStatus], Option[Tenant])] = {
    for {
      status <- pocStatusTable.getPocStatus(poc.id)
      tenant <- tenantTable.getTenant(poc.tenantId)
    } yield (status, tenant)
  }

  def doOrganisationUnitCertificateTasks(tenant: Tenant, pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.poc.typeIsApp && tenant.usageType == API)
      PoCCertCreator.pocCreationError("a poc shouldn't require client cert if tenant usageType is API", pocAndStatus)
    else if (pocAndStatus.poc.typeIsApp && !pocAndStatus.status.orgUnitCertCreated.contains(true)) {
      PoCCertCreator.createPoCOrganisationalUnitCertificate(tenant, pocAndStatus)(certHandler)
    } else {
      Task(pocAndStatus)
    }
  }

  private def doSharedAuthCertificateTasks(tenant: Tenant, pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.poc.typeIsApp && tenant.usageType == API)
      PoCCertCreator.pocCreationError(
        "a poc shouldn't require shared auth cert if tenant usageType is API",
        pocAndStatus)
    else if (pocAndStatus.poc.typeIsApp && !pocAndStatus.status.clientCertProvided.contains(true)) {
      PoCCertCreator.createPoCSharedAuthCertificate(tenant, pocAndStatus, ubirchAdminsEmails, stage)(
        certHandler,
        teamDriveService)
    } else {
      Task(pocAndStatus)
    }
  }

  //Todo: create and provide client certs
  private def process(pocAndStatus: PocAndStatus, tenant: Tenant): Task[Either[String, PocStatus]] = {
    logger.info(s"starting to process poc with id ${pocAndStatus.poc.id}")
    val creationResult = for {
      pocAndStatus1 <- doCertifyRealmRelatedTasks(pocAndStatus, tenant)
      pocAndStatus2 <- doDeviceRealmRelatedTasks(pocAndStatus1, tenant)
      statusAndPW3 <- createDevice(pocAndStatus2.poc, pocAndStatus2.status, tenant)
      status4 <- addGroupsToDevice(pocAndStatus2.poc, statusAndPW3.status)
      pocAndStatus3 <- doOrganisationUnitCertificateTasks(tenant, PocAndStatus(pocAndStatus2.poc, status4))
      pocAndStatus4 <- doSharedAuthCertificateTasks(tenant, pocAndStatus3)
      statusAndPW5 <- infoToGoClient(pocAndStatus4.poc, statusAndPW3.copy(status = pocAndStatus4.status))
      pocAndStatus6 <- infoToCertifyAPI(pocAndStatus4.poc, statusAndPW5, tenant)
      completeStatus <- downloadAndStoreLogoImage(pocAndStatus6)
    } yield completeStatus

    (for {
      completePocAndStatus <- creationResult
      _ <- quillMonixJdbcContext
        .withTransaction {
          updateStatusOfPoc(completePocAndStatus.poc, Completed) >>
            pocStatusTable.updatePocStatus(completePocAndStatus.status.copy(errorMessage = None))
        }.map(_ => logAuditEventInfo(s"updated poc and status with id ${completePocAndStatus.poc.id} by service"))
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
      pocAndStatus6 <- createPocTypeRole(pocAndStatus5)
      pocAndStatus7 <- createPocTenantTypeGroup(pocAndStatus6, tenant)
      pocAndStatus8 <- assignPocTypeRoleToGroup(pocAndStatus7)
      pocAndStatus9 <- createEmployeeGroup(pocAndStatus8)
      pocAndStatusFinal <- assignEmployeeRole(pocAndStatus9)
    } yield pocAndStatusFinal
  }

  private def downloadAndStoreLogoImage(pocAndStatus: PocAndStatus): Task[PocAndStatus] = {
    if (pocAndStatus.status.logoRequired && pocAndStatus.status.logoStored.contains(false)) {
      (for {
        logoUrl <-
          EitherT.fromOption[Task](pocAndStatus.poc.logoUrl, s"logoUrl is missing, when it should be added to Poc")
        imageByte <- EitherT.liftF(imageLoader.getImage(logoUrl.url))
        pocLogo <- EitherT(Task(PocLogo.create(pocAndStatus.poc.id, imageByte)))
        _ <- EitherT.liftF[Task, String, Unit](pocLogoRepository.createPocLogo(pocLogo))
      } yield {
        pocAndStatus.copy(status = pocAndStatus.status.copy(logoStored = Some(true)))
      }).value.onErrorHandle {
        case e: ImageLoader =>
          Left(s"can't download logo. ${e.getMessage}")
        case e => Left(e.getMessage)
      }.map {
        case Right(pocAndStatus) => pocAndStatus
        case Left(error) =>
          val errorMsg =
            s"failed to download and store pocLogo: ${pocAndStatus.poc.logoUrl.getOrElse("")}, $error"
          PocCreator.throwError(pocAndStatus, errorMsg)
      }
    } else Task(pocAndStatus)
  }

  private def handlePocCreationError[A](ex: Throwable): Task[Either[String, A]] = {
    ex match {
      case pce: PocCreationError =>
        (for {
          _ <- quillMonixJdbcContext
            .withTransaction {
              pocTable.updatePoc(pce.pocAndStatus.poc) >> pocStatusTable.updatePocStatus(pce.pocAndStatus.status)
            }.map(_ => logAuditEventInfo(s"updated poc and status with id ${pce.pocAndStatus.poc.id} by service"))
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

case class StatusAndPW(status: PocStatus, devicePassword: String)

case class PocCreationError(pocAndStatus: PocAndStatus, message: String) extends Exception(message)
