package com.ubirch.services.tenantadmin
import cats.Applicative
import cats.data.EitherT
import com.ubirch.db.tables.{ PocAdminRepository, PocRepository }
import com.ubirch.models.poc.PocAdmin
import com.ubirch.models.tenant.{ CreateWebInitiateIdRequest, Tenant, TenantId }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait TenantAdminService {
  def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String]
  def createWebInitiateId(
    tenant: Tenant,
    createWebInitiateIdRequest: CreateWebInitiateIdRequest): Task[Either[CreateWebInitiateIdErrors, UUID]]
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
  override def createWebInitiateId(
    tenant: Tenant,
    createWebInitiateIdRequest: CreateWebInitiateIdRequest): Task[Either[CreateWebInitiateIdErrors, UUID]] = {

    def isWebIdentRequired(tenant: Tenant, pocAdmin: PocAdmin): EitherT[Task, CreateWebInitiateIdErrors, Unit] = {
      if (pocAdmin.webIdentRequired) {
        EitherT.rightT[Task, CreateWebInitiateIdErrors](())
      } else {
        EitherT.leftT[Task, Unit](WebIdentNotRequired(tenant.id, pocAdmin.id))
      }
    }

    def assignWebInitiateId(pocAdminId: UUID, webInitiateId: UUID): EitherT[Task, CreateWebInitiateIdErrors, Unit] = {
      EitherT(pocAdminRepository.assignWebInitiateId(pocAdminId, webInitiateId).map(_ => Right()).onErrorRecover {
        case exception: Exception => Left(PocAdminRepositoryError(exception.getMessage))
      })
    }

    (for {
      pocAdmin <- getPocAdmin(createWebInitiateIdRequest.pocAdminId, PocAdminNotFound)
      _ <- isWebIdentRequired(tenant, pocAdmin)
      _ <-
        isPocAdminAssignedToTenant[Task, CreateWebInitiateIdErrors](tenant, pocAdmin)(PocAdminAssignedToDifferentTenant(
          tenant.id,
          pocAdmin.id))
      webInitiateId = UUID.randomUUID()
      _ <- assignWebInitiateId(pocAdmin.id, webInitiateId)
    } yield webInitiateId).value
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

sealed trait CreateWebInitiateIdErrors
case class PocAdminNotFound(pocAdminId: UUID) extends CreateWebInitiateIdErrors
case class PocAdminAssignedToDifferentTenant(tenantId: TenantId, pocAdminId: UUID) extends CreateWebInitiateIdErrors
case class WebIdentNotRequired(tenantId: TenantId, pocAdminId: UUID) extends CreateWebInitiateIdErrors
case class PocAdminRepositoryError(msg: String) extends CreateWebInitiateIdErrors
