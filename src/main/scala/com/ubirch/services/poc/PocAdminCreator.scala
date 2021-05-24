package com.ubirch.services.poc

import com.typesafe.scalalogging.{ LazyLogging, Logger }
import com.ubirch.PocConfig
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, TenantRepository }
import com.ubirch.models.poc.{ Completed, Poc, PocAdmin, PocAdminStatus, Processing, Status }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.teamdrive.model.{ Read, SpaceName, TeamDriveClient }
import monix.eval.Task
import PocAdminCreator.{ throwAndLogError, throwError }
import com.ubirch.db.context.QuillMonixJdbcContext

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
  def throwError(pocAdminAndStatus: PocAdminAndStatus, msg: String) =
    throw PocAdminCreationError(
      pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(errorMessage = Some(msg))),
      msg)
}

class PocAdminCreatorImpl @Inject() (
  pocRepository: PocRepository,
  pocAdminRepository: PocAdminRepository,
  pocAdminStatusRepository: PocAdminStatusRepository,
  tenantRepository: TenantRepository,
  teamDriveClient: TeamDriveClient,
  pocConfig: PocConfig,
  certifyHelper: CertifyHelper,
  quillMonixJdbcContext: QuillMonixJdbcContext)
  extends PocAdminCreator
  with LazyLogging {
  def createPocAdmins(): Task[PocAdminCreationResult] = {
    pocAdminRepository.getAllUncompletedPocAdmins().flatMap {
      case pocAdmins if pocAdmins.isEmpty =>
        logger.debug("no poc admins waiting for completion")
        Task(NoWaitingPocAdmin)
      case pocAdmins =>
        logger.info(s"starting to create ${pocAdmins.size} pocAdmins")
        Task.gather(pocAdmins.map(createPocAdmin))
          .map(PocAdminCreationMaybeSuccess)
    }
  }

  private def createPocAdmin(pocAdmin: PocAdmin): Task[Either[String, PocAdminStatus]] = {
    retrieveStatusAndTenant(pocAdmin).flatMap {
      case (Some(status: PocAdminStatus), Some(poc: Poc), Some(tenant: Tenant)) =>
        // Skip if the web ident has not been successful
        if (status.webIdentSuccess.contains(false)) {
          Task(Right(status))
        } else {
          updateStatusOfPoc(pocAdmin, Processing)
            .flatMap(pocAdmin => process(PocAdminAndStatus(pocAdmin, status), poc, tenant))
        }
      case (_, _, _) =>
        val errorMsg = s"cannot create poc admin with id ${pocAdmin.id} as tenant or status couldn't be found"
        logger.error(errorMsg)
        Task(Left(errorMsg))
    }.onErrorHandle { e =>
      val errorMsg =
        s"cannot create poc admin with id ${pocAdmin.id} as status and tenant couldn't be found. error: ${e.getMessage}"
      logger.error(errorMsg)
      Left(errorMsg)
    }
  }

  private def process(
    pocAdminAndStatus: PocAdminAndStatus,
    poc: Poc,
    tenant: Tenant): Task[Either[String, PocAdminStatus]] = {
    val creationResult: Task[PocAdminAndStatus] = for {
      afterCertifyTaskStatus <- doCertifyRealmRelatedTasks(pocAdminAndStatus, poc, tenant)
      completeStatus <- invitePocAdminToTeamDrive(afterCertifyTaskStatus, poc, tenant)
    } yield completeStatus

    (for {
      completePocAndStatus <- creationResult
      _ <- quillMonixJdbcContext.withTransaction {
        updateStatusOfPoc(completePocAndStatus.admin, Completed) >> pocAdminStatusRepository.updateStatus(
          completePocAndStatus.status)
      }
    } yield {
      logger.info(s"finished to create poc admin with id ${pocAdminAndStatus.admin.id}")
      Right(completePocAndStatus.status)
    }).onErrorHandleWith(handlePocAdminCreationError)
  }

  private def doCertifyRealmRelatedTasks(
    pocAdminAndStatus: PocAdminAndStatus,
    poc: Poc,
    tenant: Tenant): Task[PocAdminAndStatus] = {
    for {
      afterCreatedUserStatus <- certifyHelper.createCertifyUserWithRequiredActions(pocAdminAndStatus)
      afterAddGroups <- certifyHelper.addGroupsToCertifyUser(afterCreatedUserStatus, poc, tenant)
      finalStatus <- certifyHelper.sendEmailToCertifyUser(afterAddGroups)
    } yield finalStatus
  }

  private def invitePocAdminToTeamDrive(
    pocAdminAndStatus: PocAdminAndStatus,
    poc: Poc,
    tenant: Tenant): Task[PocAdminAndStatus] = {
    if (poc.clientCertRequired && pocAdminAndStatus.status.invitedToTeamDrive.contains(false)) {
      val spaceName = SpaceName.forTenant(pocConfig.teamDriveStage, tenant)
      teamDriveClient.getSpaceIdByName(spaceName).flatMap {
        case Some(spaceId) => teamDriveClient.inviteMember(spaceId, pocAdminAndStatus.admin.email, Read)
        case None =>
          val errorMsg = s"space was not found. $spaceName"
          logger.error(errorMsg)
          throwError(pocAdminAndStatus, errorMsg)
      }.map { _ =>
        pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(invitedToTeamDrive = Some(true)))
      }.onErrorHandle {
        case ex: PocAdminCreationError => throw ex
        case ex: Exception =>
          val errorMsg = s"failed to invite poc admin ${pocAdminAndStatus.admin.id} to TeamDrive. $ex"
          throwAndLogError(pocAdminAndStatus, errorMsg, ex, logger)
      }
    } else {
      Task(pocAdminAndStatus)
    }
  }

  private def updateStatusOfPoc(pocAdmin: PocAdmin, newStatus: Status): Task[PocAdmin] = {
    if (pocAdmin.status == newStatus) Task(pocAdmin)
    else {
      val updatedPoc = pocAdmin.copy(status = newStatus)
      pocAdminRepository
        .updatePocAdmin(updatedPoc)
        .map(_ => updatedPoc)
    }
  }

  private def retrieveStatusAndTenant(pocAdmin: PocAdmin)
    : Task[(Option[PocAdminStatus], Option[Poc], Option[Tenant])] = {
    for {
      status <- pocAdminStatusRepository.getStatus(pocAdmin.id)
      poc <- pocRepository.getPoc(pocAdmin.pocId)
      tenant <- tenantRepository.getTenant(pocAdmin.tenantId)
    } yield (status, poc, tenant)
  }

  private def handlePocAdminCreationError[A](ex: Throwable): Task[Either[String, A]] = {
    ex match {
      case pace: PocAdminCreationError =>
        (for {
          _ <- quillMonixJdbcContext.withTransaction {
            pocAdminRepository.updatePocAdmin(pace.pocAdminAndStatus.admin) >> pocAdminStatusRepository.updateStatus(
              pace.pocAdminAndStatus.status)
          }
        } yield {
          val msg =
            s"updated poc admin status after poc admin creation failed; ${pace.pocAdminAndStatus.status}, error: ${pace.message}"
          logger.error(msg)
          Left(msg)
        }).onErrorHandle { ex =>
          val errorMsg =
            s"couldn't persist poc admin and status after failed poc creation ${pace.pocAdminAndStatus.status}"
          logger.error(errorMsg, ex)
          Left(errorMsg)
        }

      case ex: Throwable =>
        val errorMsg = "unexpected error during poc admin creation; "
        logger.error(errorMsg, ex)
        Task(Left(errorMsg + ex.getMessage))
    }
  }
}

sealed trait PocAdminCreationResult
case object NoWaitingPocAdmin extends PocAdminCreationResult
case class PocAdminCreationMaybeSuccess(list: Seq[Either[String, PocAdminStatus]]) extends PocAdminCreationResult

case class PocAdminAndStatus(admin: PocAdmin, status: PocAdminStatus)
case class PocAdminCreationError(pocAdminAndStatus: PocAdminAndStatus, message: String) extends Exception(message)