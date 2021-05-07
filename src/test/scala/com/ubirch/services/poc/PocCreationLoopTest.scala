package com.ubirch.services.poc
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocStatusTestTable, PocTestTable, TenantTestTable }
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupId, GroupName }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ Tenant, TenantDeviceGroupId, TenantUserGroupId }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.{ DeviceKeycloak, UsersKeycloak }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import org.scalatest.Assertion

import scala.concurrent.duration.DurationInt

class PocCreationLoopTest extends UnitTestBase {

  "PocCreationLoop" should {
    "create pending poc successfully" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreationLoop]
        val tenantTable = injector.get[TenantTestTable]
        val pocTable = injector.get[PocTestTable]
        val pocStatusTable = injector.get[PocStatusTestTable]
        val groups = injector.get[TestKeycloakGroupsService]

        //creating needed objects
        val (poc, pocStatus, tenant) = createPocTriple()
        val updatedTenant = createNeededTenantGroups(tenant, groups)
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, updatedTenant)

        //start process
        val result: PocCreationResult = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        maybeSuccess.list.size shouldBe 1
        maybeSuccess.list.head.isRight shouldBe true
        val resultStatus = maybeSuccess.list.head.right.get

        val expectedStatus = createExpectedPocStatus(poc, resultStatus)
        maybeSuccess shouldBe PocCreationMaybeSuccess(List(Right(expectedStatus)))

        val status = await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds).get
        assertStatusAllTrue(status)
      }
    }
  }

  private def createPocTriple(): (Poc, PocStatus, Tenant) = {
    val tenant = createTenant()
    val poc = createPoc(tenantName = tenant.tenantName)
    val pocStatus = createPocStatus(poc.id)
    (poc, pocStatus, tenant)
  }

  private def createNeededTenantGroups(tenant: Tenant, groups: TestKeycloakGroupsService) = {
    val tenantGroup = CreateKeycloakGroup(GroupName(TENANT_GROUP_PREFIX + tenant.tenantName.value))
    val deviceGroup: GroupId = await(groups.createGroup(tenantGroup, DeviceKeycloak), 1.seconds).right.get
    val userGroup: GroupId = await(groups.createGroup(tenantGroup, UsersKeycloak), 1.seconds).right.get
    tenant.copy(
      deviceGroupId = TenantDeviceGroupId(deviceGroup.value),
      userGroupId = TenantUserGroupId(userGroup.value))
  }

  private def addPocTripleToRepository(
    tenantTable: TenantTestTable,
    pocTable: PocTestTable,
    pocStatusTable: PocStatusTestTable,
    poc: Poc,
    pocStatus: PocStatus,
    updatedTenant: Tenant): Unit = {

    await(tenantTable.createTenant(updatedTenant), 1.seconds)
    await(pocTable.createPoc(poc), 1.seconds)
    await(pocStatusTable.createPocStatus(pocStatus), 1.seconds)
  }

  private def createExpectedPocStatus(
    poc: Poc,
    resultStatus: PocStatus) = {
    PocStatus(
      poc.id,
      validDataSchemaGroup = true,
      userRealmRoleCreated = true,
      userRealmGroupCreated = true,
      userRealmGroupRoleAssigned = true,
      deviceRealmRoleCreated = true,
      deviceRealmGroupCreated = true,
      deviceRealmGroupRoleAssigned = true,
      deviceCreated = true,
      clientCertRequired = false,
      None,
      None,
      logoRequired = false,
      None,
      None,
      certApiProvided = true,
      goClientProvided = true,
      None,
      resultStatus.lastUpdated,
      resultStatus.created
    )
  }
  private def assertStatusAllTrue(status: PocStatus): Assertion = {
    status.deviceRealmRoleCreated shouldBe true
    status.deviceRealmGroupCreated shouldBe true
    status.deviceRealmGroupRoleAssigned shouldBe true

    status.userRealmRoleCreated shouldBe true
    status.userRealmGroupCreated shouldBe true
    status.userRealmGroupRoleAssigned shouldBe true
    status.deviceCreated shouldBe true
    status.goClientProvided shouldBe true
    status.certApiProvided shouldBe true
  }
}
