package com.ubirch.services.poc
import com.ubirch.Awaits
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.db.tables.{ PocRepositoryMock, PocStatusRepositoryMock, TenantRepositoryMock }
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.keycloak.user.{ CreateBasicKeycloakUser, UserException }
import com.ubirch.models.poc.{ Completed, Pending, Poc, PocStatus, Status }
import com.ubirch.models.tenant.{ Tenant, TenantCertifyGroupId, TenantDeviceGroupId }
import com.ubirch.models.user._
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.execution.Scheduler.Implicits.global

import java.util.UUID
import scala.concurrent.duration.DurationInt

object PocTestHelper extends Awaits {

  def createPocTriple(clientCertRequired: Boolean = false, status: Status = Pending): (Poc, PocStatus, Tenant) = {
    val tenant = createTenant()
    val poc = createPoc(tenantName = tenant.tenantName, clientCertRequired = clientCertRequired, status = status)
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
