package com.ubirch.services.poc

import com.ubirch.Awaits
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant, globalTenantName, pocTypeValue }
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository, TenantRepository }
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.keycloak.user.{ CreateBasicKeycloakUser, UserException }
import com.ubirch.models.poc.{ Pending, Poc, PocStatus, Status }
import com.ubirch.models.tenant.{ Tenant, TenantCertifyGroupId, TenantDeviceGroupId }
import com.ubirch.models.user._
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.test.TestData
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.execution.Scheduler.Implicits.global

import java.util.UUID
import scala.concurrent.duration.DurationInt

object PocTestHelper extends Awaits {

  def createPocTriple(
    tenantName: String = globalTenantName,
    status: Status = Pending,
    pocType: String = TestData.Poc.pocTypeUbVacApp): (Poc, PocStatus, Tenant) = {
    val tenant = createTenant(tenantName)
    val poc = createPoc(tenantName = tenant.tenantName, status = status, pocType = pocType)
    val pocStatus = createPocStatus(poc.id)
    (poc, pocStatus, tenant)
  }

  def createNeededTenantGroups(tenant: Tenant, groups: KeycloakGroupService): Tenant = {
    val tenantGroup = CreateKeycloakGroup(GroupName(TENANT_GROUP_PREFIX + tenant.tenantName.value))
    val deviceGroup: GroupId =
      await(groups.createGroup(DeviceKeycloak.defaultRealm, tenantGroup, DeviceKeycloak), 1.seconds).right.get
    val userGroup: GroupId =
      await(groups.createGroup(CertifyKeycloak.defaultRealm, tenantGroup, CertifyKeycloak), 1.seconds).right.get
    tenant.copy(
      deviceGroupId = TenantDeviceGroupId(deviceGroup.value),
      certifyGroupId = TenantCertifyGroupId(userGroup.value))
  }

  def addPocTripleToRepository(
    tenantTable: TenantRepository,
    pocTable: PocRepository,
    pocStatusTable: PocStatusRepository,
    poc: Poc,
    pocStatus: PocStatus,
    updatedTenant: Tenant): Unit = {

    await(tenantTable.createTenant(updatedTenant), 1.seconds)
    await(pocTable.createPoc(poc), 1.seconds)
    await(pocStatusTable.createPocStatus(pocStatus), 1.seconds)
  }

  def createNeededDeviceUser(users: KeycloakUserService, poc: Poc): Either[UserException, UserId] = {
    users.createUser(
      DeviceKeycloak.defaultRealm,
      CreateBasicKeycloakUser(FirstName(""), LastName(""), UserName(poc.getDeviceId), Email("email")),
      DeviceKeycloak).runSyncUnsafe()
  }

  def createPocStatusAllTrue(): PocStatus = {
    PocStatus(
      pocId = UUID.randomUUID(),
      certifyRoleCreated = true,
      certifyGroupCreated = true,
      certifyGroupRoleAssigned = true,
      adminGroupCreated = None,
      adminRoleAssigned = None,
      pocTypeRoleCreated = None,
      pocTypeGroupCreated = None,
      pocTypeGroupRoleAssigned = None,
      employeeGroupCreated = None,
      employeeRoleAssigned = None,
      deviceRoleCreated = true,
      deviceGroupCreated = true,
      deviceGroupRoleAssigned = true,
      deviceCreated = true,
      assignedDataSchemaGroup = true,
      assignedTrustedPocGroup = true,
      assignedDeviceGroup = true,
      clientCertRequired = false,
      orgUnitCertCreated = None,
      clientCertCreated = None,
      clientCertProvided = None,
      logoRequired = false,
      logoStored = None,
      goClientProvided = true,
      certifyApiProvided = true,
      errorMessage = None
    )
  }

}
