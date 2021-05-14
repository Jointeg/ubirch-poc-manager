package com.ubirch.services.poc
import com.ubirch.Awaits
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.db.tables.{
  PocRepository,
  PocRepositoryMock,
  PocStatusRepository,
  PocStatusRepositoryMock,
  TenantRepositoryMock
}
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ Tenant, TenantCertifyGroupId, TenantDeviceGroupId }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.execution.Scheduler.Implicits.global
import org.scalatest.Assertion
import org.scalatest.Matchers.convertToAnyShouldWrapper

import scala.concurrent.duration.DurationInt

object PocTestHelper extends Awaits {

  def createPocTriple(): (Poc, PocStatus, Tenant) = {
    val tenant = createTenant()
    val poc = createPoc(tenantName = tenant.tenantName)
    val pocStatus = createPocStatus(poc.id)
    (poc, pocStatus, tenant)
  }

  def createNeededTenantGroups(tenant: Tenant, groups: TestKeycloakGroupsService): Tenant = {
    val tenantGroup = CreateKeycloakGroup(GroupName(TENANT_GROUP_PREFIX + tenant.tenantName.value))
    val deviceGroup: GroupId = await(groups.createGroup(tenantGroup, DeviceKeycloak), 1.seconds).right.get
    val userGroup: GroupId = await(groups.createGroup(tenantGroup, CertifyKeycloak), 1.seconds).right.get
    tenant.copy(
      deviceGroupId = TenantDeviceGroupId(deviceGroup.value),
      certifyGroupId = TenantCertifyGroupId(userGroup.value))
  }

  def addPocTripleToRepository(
    tenantTable: TenantRepositoryMock,
    pocTable: PocRepositoryMock,
    pocStatusTable: PocStatusRepositoryMock,
    poc: Poc,
    pocStatus: PocStatus,
    updatedTenant: Tenant): Unit = {

    await(tenantTable.createTenant(updatedTenant), 1.seconds)
    await(pocTable.createPoc(poc), 1.seconds)
    await(pocStatusTable.createPocStatus(pocStatus), 1.seconds)
  }

}
