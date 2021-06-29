package com.ubirch.services.poc

import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.PocConfig
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, TenantRepository }
import com.ubirch.models.common.{ ProcessingElements, WaitingForNewElements }
import com.ubirch.models.poc._
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.PocAdminCreator.throwAndLogError
import com.ubirch.services.teamdrive.model.{ Read, SpaceId, SpaceName, TeamDriveClient }
import com.ubirch.util.PocAuditLogging
import monix.eval.Task
import org.joda.time.DateTime

import javax.inject.Inject

trait PocAdminCreator {
  def createPocAdmins(): Task[Unit]
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

  def createPocAdmins(): Task[Unit] = {
    adminRepository.getAllUncompletedPocAdminsIds().flatMap {
      case pocAdminsIds if pocAdminsIds.isEmpty =>
        logger.debug("no poc admins waiting for completion")
        Task(PocAdminCreationLoop.loopState.set(WaitingForNewElements(DateTime.now(), "PoC Admin"))).void
      case pocAdminsIds =>
        Task.sequence(pocAdminsIds.map(pocAdminId => {
          (for {
            _ <- Task(logger.info(s"Starting to process PoC Admin with id $pocAdminId"))
            _ <- Task.cancelBoundary
            _ <- Task(ProcessingElements(DateTime.now(), "PoC Admin", pocAdminId.toString))
            pocAdmin <- adminRepository.unsafeGetUncompletedPocAdminById(pocAdminId)
            _ <- createPocAdmin(pocAdmin).uncancelable
          } yield ()).onErrorHandle(ex => {
            logger.error(s"Unexpected error has happened while processing PoC Admin with id $pocAdminId", ex)
            ()
          })
        })).void
    }
  }

  private def incrementCreationAttemptCounter(admin: PocAdmin) = {
    if (admin.creationAttempts >= 10) {
      Task(logger.warn(
        s"PoC Admin with ID ${admin.id} has exceeded maximum creation attempts number. Changing its status to Aborted.")) >>
        adminRepository.incrementCreationAttempts(admin.id) >> adminRepository.updatePocAdmin(admin.copy(status =
          Aborted)).void
    } else {
      adminRepository.incrementCreationAttempts(admin.id)
    }
  }

  private def createPocAdmin(admin: PocAdmin): Task[Either[String, PocAdminStatus]] = {
    import cats.syntax.all._
    retrieveStatusAndTenant(admin).flatMap {
      case (Some(status: PocAdminStatus), Some(poc: Poc), Some(tenant: Tenant)) =>
        if (poc.status == Aborted) {
          logger.debug(s"s cannot start processing admin ${admin.id} as related PoC ${poc.id} is in Aborted state")
          incrementCreationAttemptCounter(admin) >> adminRepository.updatePocAdmin(
            admin.copy(status = Aborted)) >> Task(
            logAndGetLeft(s"Cannot create admin ${admin.id} as PoC is in Aborted state"))
        } else if (poc.status != Completed) {
          logger.debug(s"cannot start processing admin ${admin.id} as poc is not completed yet")
          Task(Right(status))
        } else if (status.webIdentSuccess.contains(false)) {
          logger.debug(s"cannot start processing admin ${admin.id} as webident is not finished yet")
          incrementCreationAttemptCounter(admin) >> Task(Right(status))
        } else {
          for {
            pocAdmin <- updateStatusOfAdmin(admin, Processing)
            result <- process(PocAdminAndStatus(pocAdmin, status.copy(errorMessage = None)), poc, tenant)
            _ <- result.leftTraverse(_ => incrementCreationAttemptCounter(pocAdmin))
          } yield result
        }
      case (_, _, _) =>
        Task(logAndGetLeft(s"cannot create admin ${admin.id} as tenant, poc or status couldn't be found"))
    }.onErrorHandleWith { e =>
      incrementCreationAttemptCounter(admin) >> Task(
        logAndGetLeft(s"cannot create admin ${admin.id}, due to: ${e.getMessage}"))
    }
  }

  private def process(aAs: PocAdminAndStatus, poc: Poc, tenant: Tenant): Task[Either[String, PocAdminStatus]] = {

    val creationResult =
      for {
        aAs1 <- createCertifyUserWithRequiredActions(aAs)
        aAs2 <- invitePocAdminToTeamDrive(aAs1, poc, tenant)
        aAs3 <- invitePocAdminToStaticTeamDrive(aAs2, poc)
        aAs4 <- addGroupsToCertifyUser(aAs3, poc)
        completeStatus <- sendEmailToCertifyUser(aAs4)
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
    if (poc.typeIsApp && aAs.status.invitedToTeamDrive.contains(false)) {
      val spaceName = SpaceName.ofPoc(pocConfig.teamDriveStage, tenant, poc)
      teamDriveClient.withLogin {
        teamDriveClient.getSpaceByNameWithActivation(spaceName).flatMap {
          case Some(space) =>
            teamDriveClient.inviteMember(SpaceId(space.id), aAs.admin.email, pocConfig.certWelcomeMessage, Read)
              .map { _ =>
                logAuditEventInfo(s"invited poc admin ${aAs.admin.id} to TeamDrive space $spaceName")
              }
          case None => throwAndLogError(aAs, s"space was not found. $spaceName", logger)
        }
      }.map(_ => aAs.copy(status = aAs.status.copy(invitedToTeamDrive = Some(true))))
        .onErrorHandle {
          case ex: PocAdminCreationError => throw ex
          case ex: Exception =>
            throwAndLogError(
              aAs,
              s"failed to invite poc admin ${aAs.admin.id} to the cert space in TeamDrive. $spaceName. $ex",
              ex,
              logger)
        }
    } else Task(aAs)
  }

  private def invitePocAdminToStaticTeamDrive(aAs: PocAdminAndStatus, poc: Poc): Task[PocAdminAndStatus] = {
    pocConfig.pocTypeStaticSpaceNameMap.get(poc.pocType).fold(Task(aAs)) { name =>
      if (poc.typeIsApp && aAs.status.invitedToStaticTeamDrive.contains(false)) {
        val spaceName = SpaceName.of(pocConfig.teamDriveStage, name)
        teamDriveClient.withLogin {
          teamDriveClient.getSpaceByNameWithActivation(spaceName).flatMap {
            case Some(space) =>
              teamDriveClient.inviteMember(
                SpaceId(space.id),
                aAs.admin.email,
                pocConfig.staticAssetsWelcomeMessage,
                Read)
                .map { _ =>
                  logAuditEventInfo(s"invited poc admin ${aAs.admin.id} to TeamDrive space $spaceName")
                }
            case None => throwAndLogError(aAs, s"space was not found. $spaceName", logger)
          }
        }.map(_ => aAs.copy(status = aAs.status.copy(invitedToStaticTeamDrive = Some(true))))
          .onErrorHandle {
            case ex: PocAdminCreationError => throw ex
            case ex: Exception =>
              throwAndLogError(
                aAs,
                s"failed to invite poc admin ${aAs.admin.id} to the static space in TeamDrive. $spaceName. $ex",
                ex,
                logger)
          }
      } else Task(aAs)
    }
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

case class PocAdminAndStatus(admin: PocAdmin, status: PocAdminStatus)
case class PocAdminCreationError(pocAdminAndStatus: PocAdminAndStatus, message: String) extends Exception(message)
