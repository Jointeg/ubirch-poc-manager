package com.ubirch.services.tenantadmin
import cats.data.EitherT
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository }
import com.ubirch.models.poc.{ PocAdmin, PocAdminStatus }
import com.ubirch.models.tenant.{ Tenant, TenantId, UpdateWebidentIdentifierRequest }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait TenantAdminService {
  def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String]
  def updateWebidentIdentifier(
    tenant: Tenant,
    request: UpdateWebidentIdentifierRequest): Task[Either[UpdateWebidentIdError, Unit]]
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
  override def updateWebidentIdentifier(
    tenant: Tenant,
    request: UpdateWebidentIdentifierRequest): Task[Either[UpdateWebidentIdError, Unit]] = {

    val getPocAdmin: EitherT[Task, UpdateWebidentIdError, PocAdmin] =
      EitherT(pocAdminRepository.getPocAdmin(request.pocAdminId).map(_.toRight(UnknownPocAdmin(request.pocAdminId))))

    def isPocAdminAssignedToRequestingTenant(pocAdmin: PocAdmin): EitherT[Task, UpdateWebidentIdError, Unit] =
      if (pocAdmin.tenantId == tenant.id) EitherT.right(Task(()))
      else EitherT.left(Task(PocAdminIsNotAssignedToRequestingTenant(pocAdmin.tenantId, tenant.id)))

    def getPocAdminStatus(pocAdmin: PocAdmin): EitherT[Task, UpdateWebidentIdError, PocAdminStatus] =
      EitherT(pocAdminStatusRepository.getStatus(pocAdmin.id).map(_.toRight(NotExistingPocAdminStatus(pocAdmin.id))))

    (for {
      pocAdmin <- getPocAdmin
      _ <- isPocAdminAssignedToRequestingTenant(pocAdmin)
      _ <- EitherT.right[UpdateWebidentIdError](pocAdminRepository.updatePocAdmin(pocAdmin.copy(webIdentIdentifier =
        Some(request.webidentIdentifier.toString))))
      pocAdminStatus <- getPocAdminStatus(pocAdmin)
      _ <- EitherT.right[UpdateWebidentIdError](
        pocAdminStatusRepository.updateStatus(pocAdminStatus.copy(webIdentIdentified = Some(true))))
    } yield ()).value
  }
}

sealed trait UpdateWebidentIdError
case class UnknownPocAdmin(id: UUID) extends UpdateWebidentIdError
case class PocAdminIsNotAssignedToRequestingTenant(pocAdminTenantId: TenantId, requestingTenantId: TenantId)
  extends UpdateWebidentIdError
case class NotExistingPocAdminStatus(pocAdminId: UUID) extends UpdateWebidentIdError
