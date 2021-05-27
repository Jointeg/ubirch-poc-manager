package com.ubirch.services.poc

import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.PocConfig
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, TenantRepository }
import com.ubirch.models.poc._
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.PocAdminCreator.throwAndLogError
import com.ubirch.services.teamdrive.model.{ Read, SpaceName, TeamDriveClient }
import com.ubirch.util.PocAuditLogging
import monix.eval.Task

import javax.inject.Inject

trait PocAdminCreator {
  def createPocAdmins(): Task[PocAdminCreationResult]
}

object PocAdminCreator {
  @throws[PocAdminCreationError]
  def throwAndLogError(pocAdminAndStatus: PocAdminAndStatus, msg: String, ex: Throwable, logger: Logger): Nothing = {
    logger.error(msg, ex)
    throwError(pocAdminAndStatus, msg + ex.getMessage)
  }

  @throws[PocAdminCreationError]
  def throwAndLogError(pocAdminAndStatus: PocAdminAndStatus, msg: String, logger: Logger): Nothing = {
    logger.error(msg)
    throwError(pocAdminAndStatus, msg)
  }

  @throws[PocAdminCreationError]
  def throwError(pocAdminAndStatus: PocAdminAndStatus, msg: String) =
    throw PocAdminCreationError(
      pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(errorMessage = Some(msg))),
      msg)
}

class PocAdminCreatorImpl @Inject() (
  pocRepository: PocRepository,
  adminRepository: PocAdminRepository,
  adminStatusRepository: PocAdminStatusRepository,
  tenantRepository: TenantRepository,
  teamDriveClient: TeamDriveClient,
  pocConfig: PocConfig,
  certifyHelper: AdminCertifyHelper,
  quillMonixJdbcContext: QuillMonixJdbcContext)
  extends PocAdminCreator
  with LazyLogging
  with PocAuditLogging {

  import certifyHelper._

  def createPocAdmins(): Task[PocAdminCreationResult] = {
    adminRepository.getAllUncompletedPocAdmins().flatMap {
      case pocAdmins if pocAdmins.isEmpty =>
        logger.debug("no poc admins waiting for completion")
        Task(NoWaitingPocAdmin)
      case pocAdmins =>
        logger.info(s"starting to create ${pocAdmins.size} pocAdmins")
        Task.gather(pocAdmins.map(createPocAdmin))
          .map(PocAdminCreationMaybeSuccess)
    }
  }

  private def createPocAdmin(admin: PocAdmin): Task[Either[String, PocAdminStatus]] = {
    retrieveStatusAndTenant(admin).flatMap {
      case (Some(status: PocAdminStatus), Some(poc: Poc), Some(tenant: Tenant)) =>
        // Skip if the web ident has not been successful
        if (status.webIdentSuccess.contains(false)) {
          logger.info(s"cannot process admin ${admin.id} as webident is not finished yet")
          Task(Right(status))
        } else {
          updateStatusOfAdmin(admin, Processing)
            .flatMap(pocAdmin => process(PocAdminAndStatus(pocAdmin, status), poc, tenant))
        }
      case (_, _, _) =>
        Task(logAndGetLeft(s"cannot create admin ${admin.id} as tenant, poc or status couldn't be found"))
    }.onErrorHandle { e => logAndGetLeft(s"cannot create admin ${admin.id}, due to: ${e.getMessage}") }
  }

  private def process(aAs: PocAdminAndStatus, poc: Poc, tenant: Tenant): Task[Either[String, PocAdminStatus]] = {

    val creationResult =
      for {
        aAs1 <- createCertifyUserWithRequiredActions(aAs)
        aAs2 <- addGroupsToCertifyUser(aAs1, poc)
        aAs3 <- sendEmailToCertifyUser(aAs2)
        completeStatus <- invitePocAdminToTeamDrive(aAs3, poc, tenant)
      } yield completeStatus

    (for {
      completePocAndStatus <- creationResult
      _ <- quillMonixJdbcContext
        .withTransaction {
          updateStatusOfAdmin(completePocAndStatus.admin, Completed) >>
            adminStatusRepository.updateStatus(completePocAndStatus.status.copy(errorMessage = None))
        }.map(_ =>
          logAuditEventInfo(s"updated poc admin and status with id ${completePocAndStatus.admin.id} by service"))
    } yield {
      logger.info(s"finished to create poc admin with id ${aAs.admin.id}")
      Right(completePocAndStatus.status)
    }).onErrorHandleWith(handlePocAdminCreationError)
  }

  private def invitePocAdminToTeamDrive(aAs: PocAdminAndStatus, poc: Poc, tenant: Tenant): Task[PocAdminAndStatus] = {

    if (poc.clientCertRequired && aAs.status.invitedToTeamDrive.contains(false)) {
      val spaceName = SpaceName.forPoc(pocConfig.teamDriveStage, tenant, poc)
      teamDriveClient.getSpaceIdByName(spaceName).flatMap {
        case Some(spaceId) => teamDriveClient.inviteMember(spaceId, aAs.admin.email, Read)
        case None          => throwAndLogError(aAs, s"space was not found. $spaceName", logger)
      }.map(_ => aAs.copy(status = aAs.status.copy(invitedToTeamDrive = Some(true))))
        .onErrorHandle {
          case ex: PocAdminCreationError => throw ex
          case ex: Exception =>
            throwAndLogError(aAs, s"failed to invite poc admin ${aAs.admin.id} to TeamDrive. $ex", ex, logger)
        }
    } else Task(aAs)
  }

  private def updateStatusOfAdmin(pocAdmin: PocAdmin, newStatus: Status): Task[PocAdmin] = {
    if (pocAdmin.status == newStatus) Task(pocAdmin)
    else {
      val updatedPoc = pocAdmin.copy(status = newStatus)
      adminRepository
        .updatePocAdmin(updatedPoc)
        .map(_ => updatedPoc)
    }
  }

  private def retrieveStatusAndTenant(pocAdmin: PocAdmin)
    : Task[(Option[PocAdminStatus], Option[Poc], Option[Tenant])] = {
    for {
      status <- adminStatusRepository.getStatus(pocAdmin.id)
      poc <- pocRepository.getPoc(pocAdmin.pocId)
      tenant <- tenantRepository.getTenant(pocAdmin.tenantId)
    } yield (status, poc, tenant)
  }

  private def handlePocAdminCreationError[A](ex: Throwable): Task[Either[String, A]] = {
    ex match {
      case pace: PocAdminCreationError =>
        (for {
          _ <- quillMonixJdbcContext
            .withTransaction {
              adminRepository.updatePocAdmin(pace.pocAdminAndStatus.admin) >>
                adminStatusRepository.updateStatus(pace.pocAdminAndStatus.status)
            }.map(_ =>
              logAuditEventInfo(s"updated poc admin and status with id ${pace.pocAdminAndStatus.admin.id} by service"))
        } yield {
          logAndGetLeft(
            s"persisted admin and status after creation error; ${pace.pocAdminAndStatus.status}: ${pace.message}")
        }).onErrorHandle { ex =>
          logAndGetLeft(s"couldn't persist admin and status after creation error ${pace.pocAdminAndStatus.status}", ex)
        }

      case ex: Throwable => Task(logAndGetLeft("unexpected error during poc admin creation; ", ex))
    }
  }

  private def logAndGetLeft(errorMsg: String): Left[String, Nothing] = {
    logger.error(errorMsg)
    Left(errorMsg)
  }

  private def logAndGetLeft(errorMsg: String, ex: Throwable): Left[String, Nothing] = {
    logger.error(errorMsg, ex)
    Left(errorMsg + ex.getMessage)
  }
}

sealed trait PocAdminCreationResult
case object NoWaitingPocAdmin extends PocAdminCreationResult
case class PocAdminCreationMaybeSuccess(list: Seq[Either[String, PocAdminStatus]]) extends PocAdminCreationResult

case class PocAdminAndStatus(admin: PocAdmin, status: PocAdminStatus)
case class PocAdminCreationError(pocAdminAndStatus: PocAdminAndStatus, message: String) extends Exception(message)
