package com.ubirch.services.tenantadmin
import com.ubirch.db.tables.PocRepository
import com.ubirch.models.tenant.Tenant
import monix.eval.Task

import javax.inject.Inject

trait TenantAdminService {
  def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String]
}

class DefaultTenantAdminService @Inject() (pocRepository: PocRepository) extends TenantAdminService {
  private val simplifiedDeviceInfoCSVHeader = """"externalId"; "pocName"; "deviceId""""

  override def getSimplifiedDeviceInfoAsCSV(tenant: Tenant): Task[String] = {
    for {
      devicesInfo <- pocRepository.getPoCsSimplifiedDeviceInfoByTenant(tenant.id)
      devicesInCSVFormat = devicesInfo.map(_.toCSVFormat)
    } yield simplifiedDeviceInfoCSVHeader + "\n" + devicesInCSVFormat.mkString("\n")
  }
}
