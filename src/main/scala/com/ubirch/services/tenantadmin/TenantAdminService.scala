package com.ubirch.services.tenantadmin
import cats.data.EitherT
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository }
import com.ubirch.models.poc.{ PocAdmin, PocAdminStatus }
import com.ubirch.models.tenant.{ CreateWebIdentInitiateIdRequest, Tenant, TenantId, UpdateWebIdentIdRequest }
import cats.Applicative
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait TenantAdminService {
  def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String]
  def updateWebIdentId(
    tenant: Tenant,
    request: UpdateWebIdentIdRequest): Task[Either[UpdateWebIdentIdError, Unit]]
  def createWebIdentInitiateId(
    tenant: Tenant,
    createWebIdentInitiateIdRequest: CreateWebIdentInitiateIdRequest)
    : Task[Either[CreateWebIdentInitiateIdErrors, UUID]]
}

class DefaultTenantAdminService @Inject() (
  pocRepository: PocRepository,
  pocAdminRepository: PocAdminRepository,
  pocAdminStatusRepository: PocAdminStatusRepository)
  extends TenantAdminService {
  private val simplifiedDeviceInfoCSVHeader = """"externalId"; "pocName"; "deviceId""""

  override def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String] = {
    for {
      devicesInfo <- pocRepository.getPoCsSimplifiedDeviceInfoByTenant(tenant.id)
      devicesInCSVFormat = devicesInfo.map(_.toCSVFormat)
    } yield simplifiedDeviceInfoCSVHeader + "\n" + devicesInCSVFormat.mkString("\n")
  }
  override def createWebIdentInitiateId(
    tenant: Tenant,
    createWebIdentInitiateIdRequest: CreateWebIdentInitiateIdRequest)
    : Task[Either[CreateWebIdentInitiateIdErrors, UUID]] = {

    def isWebIdentRequired(tenant: Tenant, pocAdmin: PocAdmin): EitherT[Task, CreateWebIdentInitiateIdErrors, Unit] = {
      if (pocAdmin.webIdentRequired) {
        EitherT.rightT[Task, CreateWebIdentInitiateIdErrors](())
      } else {
        EitherT.leftT[Task, Unit](WebIdentNotRequired(tenant.id, pocAdmin.id))
      }
    }

    def assignWebIdentInitiateId(
      pocAdminId: UUID,
      webIdentInitiateId: UUID): EitherT[Task, CreateWebIdentInitiateIdErrors, Unit] = {
      EitherT(pocAdminRepository.assignWebIdentInitiateId(pocAdminId, webIdentInitiateId).map(_ =>
        Right(())).onErrorRecover {
        case exception: Exception => Left(PocAdminRepositoryError(exception.getMessage))
      })
    }

    (for {
      pocAdmin <- getPocAdmin(createWebIdentInitiateIdRequest.pocAdminId, PocAdminNotFound)
      _ <- isWebIdentRequired(tenant, pocAdmin)
      _ <-
        isPocAdminAssignedToTenant[Task, CreateWebIdentInitiateIdErrors](tenant, pocAdmin)(
          PocAdminAssignedToDifferentTenant(
            tenant.id,
            pocAdmin.id))
      webIdentInitiateId = UUID.randomUUID()
      _ <- assignWebIdentInitiateId(pocAdmin.id, webIdentInitiateId)
    } yield webIdentInitiateId).value
  }

  override def updateWebIdentId(
    tenant: Tenant,
    request: UpdateWebIdentIdRequest): Task[Either[UpdateWebIdentIdError, Unit]] = {
    import UpdateWebIdentIdError._

    def getPocAdminStatus(pocAdmin: PocAdmin): EitherT[Task, UpdateWebIdentIdError, PocAdminStatus] =
      EitherT(
        pocAdminStatusRepository.getStatus(pocAdmin.id).map(_.toRight(NotExistingPocAdminStatus(pocAdmin.id)))
      )

    def updatePocAdminWebIdentId(pocAdmin: PocAdmin) = {
      EitherT.right[UpdateWebIdentIdError](pocAdminRepository.updatePocAdmin(pocAdmin.copy(webIdentId =
        Some(request.webIdentId.toString))))
    }

    def resetPocAdminWebIdentId(pocAdmin: PocAdmin) = {
      EitherT.right[UpdateWebIdentIdError](pocAdminRepository.updatePocAdmin(pocAdmin.copy(webIdentId = None)))
    }

    def updatePocAdminStatus(pocAdminStatus: PocAdminStatus) = {
      EitherT.right[UpdateWebIdentIdError](
        pocAdminStatusRepository.updateStatus(pocAdminStatus.copy(webIdentIdentified = Some(true))))
    }

    def isSameWebIdentInitialId(tenant: Tenant, pocAdmin: PocAdmin) = {
      if (pocAdmin.webIdentInitiateId.contains(request.webIdentInitiateId)) {
        EitherT.rightT[Task, UpdateWebIdentIdError](())
      } else {
        EitherT.leftT[Task, UpdateWebIdentIdError](DifferentWebIdentInitialId(
          request.webIdentInitiateId,
          tenant,
          pocAdmin))
      }
    }

    (for {
      pocAdmin <- getPocAdmin(request.pocAdminId, UnknownPocAdmin)
      _ <- isPocAdminAssignedToTenant[Task, UpdateWebIdentIdError](tenant, pocAdmin)(
        PocAdminIsNotAssignedToRequestingTenant(pocAdmin.tenantId, tenant.id))
      _ <- isSameWebIdentInitialId(tenant, pocAdmin)
      pocAdminStatus <- getPocAdminStatus(pocAdmin)
      _ <- updatePocAdminWebIdentId(pocAdmin)
      _ <- updatePocAdminStatus(pocAdminStatus)
    } yield ()).value
  }

  private def isPocAdminAssignedToTenant[F[_]: Applicative, E](
    tenant: Tenant,
    pocAdmin: PocAdmin)(error: => E): EitherT[F, E, Unit] = {
    if (pocAdmin.tenantId == tenant.id) {
      EitherT.rightT[F, E](())
    } else {
      EitherT.leftT[F, Unit](error)
    }
  }

  private def getPocAdmin[E](pocAdminId: UUID, error: UUID => E): EitherT[Task, E, PocAdmin] = {
    EitherT(pocAdminRepository.getPocAdmin(pocAdminId).map(_.toRight(error(pocAdminId))))
  }
}

sealed trait CreateWebIdentInitiateIdErrors
case class PocAdminNotFound(pocAdminId: UUID) extends CreateWebIdentInitiateIdErrors
case class PocAdminAssignedToDifferentTenant(tenantId: TenantId, pocAdminId: UUID)
  extends CreateWebIdentInitiateIdErrors
case class WebIdentNotRequired(tenantId: TenantId, pocAdminId: UUID) extends CreateWebIdentInitiateIdErrors
case class PocAdminRepositoryError(msg: String) extends CreateWebIdentInitiateIdErrors

sealed trait UpdateWebIdentIdError
object UpdateWebIdentIdError {
  case class UnknownPocAdmin(id: UUID) extends UpdateWebIdentIdError
  case class PocAdminIsNotAssignedToRequestingTenant(pocAdminTenantId: TenantId, requestingTenantId: TenantId)
    extends UpdateWebIdentIdError
  case class DifferentWebIdentInitialId(requestWebIdentInitialId: UUID, tenant: Tenant, pocAdmin: PocAdmin)
    extends UpdateWebIdentIdError
  case class NotExistingPocAdminStatus(pocAdminId: UUID) extends UpdateWebIdentIdError
}
