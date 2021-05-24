package com.ubirch.services.poc
import com.ubirch.db.tables.{ PocRepositoryMock, PocStatusRepositoryMock, TenantRepositoryMock }
import com.ubirch.UnitTestBase
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.keycloak.user.CreateBasicKeycloakUser
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.user.{ Email, FirstName, LastName, UserName }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.keycloak.users.TestKeycloakUserService
import com.ubirch.services.poc.PocTestHelper._
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.reactive.Observable
import org.scalatest.Assertion

import scala.concurrent.duration.DurationInt

class PocCreationLoopTest extends UnitTestBase {

  "Poc Creation Loop" should {
    "create pending poc first after adding it to database" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreationLoop]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val groups = injector.get[TestKeycloakGroupsService]
        val keyCloakRoleService = injector.get[KeycloakRolesService]

        val users = injector.get[TestKeycloakUserService]

        //creating needed objects
        val (poc, pocStatus, tenant) = createPocTriple()
        val updatedTenant = createNeededTenantGroups(tenant, groups)
        createNeededDeviceUser(users, poc)

        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + tenant.tenantName.value)),
          DeviceKeycloak).runSyncUnsafe(3.seconds)
        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + tenant.tenantName.value)),
          CertifyKeycloak).runSyncUnsafe(3.seconds)

        //start process
        val pocCreation = loop.startPocCreationLoop(resp => Observable(resp)).subscribe()
        Thread.sleep(4000)
        await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds) shouldBe None
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, updatedTenant)
        Thread.sleep(3000)
        val status = await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds).get
        assertStatusAllTrue(status)
        pocCreation.cancel()
      }
    }
  }

  private def createNeededDeviceUser(users: TestKeycloakUserService, poc: Poc) = {
    users.createUser(
      CreateBasicKeycloakUser(FirstName(""), LastName(""), UserName(poc.getDeviceId), Email("email")),
      DeviceKeycloak).runSyncUnsafe()
  }

  private def assertStatusAllTrue(status: PocStatus): Assertion = {
    status.deviceRoleCreated shouldBe true
    status.deviceGroupCreated shouldBe true
    status.deviceGroupRoleAssigned shouldBe true

    status.certifyRoleCreated shouldBe true
    status.certifyGroupCreated shouldBe true
    status.certifyGroupRoleAssigned shouldBe true
    status.deviceCreated shouldBe true
    status.goClientProvided shouldBe true
    status.certifyApiProvided shouldBe true
  }
}
