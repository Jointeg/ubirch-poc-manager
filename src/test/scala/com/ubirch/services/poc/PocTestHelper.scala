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
import com.ubirch.models.keycloak.user.{ CreateKeycloakUser, UserException }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ Tenant, TenantCertifyGroupId, TenantDeviceGroupId }
import com.ubirch.models.user.{ Email, FirstName, LastName, UserId, UserName }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.users.TestKeycloakUserService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.execution.Scheduler.Implicits.global

import java.util.UUID
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

  def createNeededDeviceUser(users: TestKeycloakUserService, poc: Poc): Either[UserException, UserId] = {
    users.createUser(
      CreateKeycloakUser(FirstName(""), LastName(""), UserName(poc.getDeviceId), Email("email")),
      DeviceKeycloak).runSyncUnsafe()
  }

  def createPocStatusAllTrue(): PocStatus = {
    PocStatus(
      pocId = UUID.randomUUID(),
      certifyRoleCreated = true,
      certifyGroupCreated = true,
      certifyGroupRoleAssigned = true,
      certifyGroupTenantRoleAssigned = true,
      deviceRoleCreated = true,
      deviceGroupCreated = true,
      deviceGroupRoleAssigned = true,
      deviceGroupTenantRoleAssigned = true,
      deviceCreated = true,
      assignedDataSchemaGroup = true,
      assignedDeviceGroup = true,
      clientCertRequired = false,
      orgUnitCertCreated = None,
      clientCertCreated = None,
      clientCertProvided = None,
      logoRequired = false,
      logoReceived = None,
      logoStored = None,
      goClientProvided = true,
      certifyApiProvided = true,
      errorMessage = None
    )
  }

}
