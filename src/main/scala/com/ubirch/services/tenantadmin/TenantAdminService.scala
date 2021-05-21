package com.ubirch.services.tenantadmin
import cats.Applicative
import cats.data.EitherT
import com.ubirch.db.tables.{ PocAdminRepository, PocRepository }
import com.ubirch.models.poc.PocAdmin
import com.ubirch.models.tenant.{ CreateWebIdentInitiateIdRequest, Tenant, TenantId }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait TenantAdminService {
  def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String]
  def createWebIdentInitiateId(
    tenant: Tenant,
    createWebIdentInitiateIdRequest: CreateWebIdentInitiateIdRequest)
    : Task[Either[CreatewebIdentInitiateIdErrors, UUID]]
}

class DefaultTenantAdminService @Inject() (pocRepository: PocRepository, pocAdminRepository: PocAdminRepository)
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
    : Task[Either[CreatewebIdentInitiateIdErrors, UUID]] = {

    def isWebIdentRequired(tenant: Tenant, pocAdmin: PocAdmin): EitherT[Task, CreatewebIdentInitiateIdErrors, Unit] = {
      if (pocAdmin.webIdentRequired) {
        EitherT.rightT[Task, CreatewebIdentInitiateIdErrors](())
      } else {
        EitherT.leftT[Task, Unit](WebIdentNotRequired(tenant.id, pocAdmin.id))
      }
    }

    def assignWebIdentInitiateId(
      pocAdminId: UUID,
      webIdentInitiateId: UUID): EitherT[Task, CreatewebIdentInitiateIdErrors, Unit] = {
      EitherT(pocAdminRepository.assignWebIdentInitiateId(pocAdminId, webIdentInitiateId).map(_ =>
        Right(())).onErrorRecover {
        case exception: Exception => Left(PocAdminRepositoryError(exception.getMessage))
      })
    }

    (for {
      pocAdmin <- getPocAdmin(createWebIdentInitiateIdRequest.pocAdminId, PocAdminNotFound)
      _ <- isWebIdentRequired(tenant, pocAdmin)
      _ <-
        isPocAdminAssignedToTenant[Task, CreatewebIdentInitiateIdErrors](tenant, pocAdmin)(
          PocAdminAssignedToDifferentTenant(
            tenant.id,
            pocAdmin.id))
      webIdentInitiateId = UUID.randomUUID()
      _ <- assignWebIdentInitiateId(pocAdmin.id, webIdentInitiateId)
    } yield webIdentInitiateId).value
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

sealed trait CreatewebIdentInitiateIdErrors
case class PocAdminNotFound(pocAdminId: UUID) extends CreatewebIdentInitiateIdErrors
case class PocAdminAssignedToDifferentTenant(tenantId: TenantId, pocAdminId: UUID)
  extends CreatewebIdentInitiateIdErrors
case class WebIdentNotRequired(tenantId: TenantId, pocAdminId: UUID) extends CreatewebIdentInitiateIdErrors
case class PocAdminRepositoryError(msg: String) extends CreatewebIdentInitiateIdErrors
