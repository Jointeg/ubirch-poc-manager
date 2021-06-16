package com.ubirch.services.poc
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocRepositoryMock, PocStatusRepositoryMock, TenantRepositoryMock }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.keycloak.user.CreateBasicKeycloakUser
import com.ubirch.models.poc.{ LogoURL, Poc, PocStatus }
import com.ubirch.models.user.{ Email, FirstName, LastName, UserName }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.keycloak.users.{ KeycloakUserService, TestKeycloakUserService }
import com.ubirch.services.poc.PocTestHelper._
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.test.TestData
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.reactive.Observable
import org.scalatest.Assertion

import java.net.URL
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
        val (poc, pocStatus, tenant) = createPocTriple(pocType = TestData.Poc.pocTypeBmgVacApi)
        val updatedTenant = createNeededTenantGroups(tenant, groups)
        createNeededDeviceUser(users, poc)

        keyCloakRoleService.createNewRole(
          DeviceKeycloak.defaultRealm,
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + tenant.tenantName.value)),
          DeviceKeycloak).runSyncUnsafe(3.seconds)
        keyCloakRoleService.createNewRole(
          CertifyKeycloak.defaultRealm,
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + tenant.tenantName.value)),
          CertifyKeycloak).runSyncUnsafe(3.seconds)

        //start process
        val pocCreation = loop.startPocCreationLoop(resp => Observable(resp))
        awaitForTwoTicks(pocCreation)
        await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds) shouldBe None
        val updatedPoc = poc.copy(logoUrl = Some(LogoURL(
          new URL("https://www.scala-lang.org/resources/img/frontpage/scala-spiral.png"))))
        val updatedStatus = pocStatus.copy(logoRequired = true, logoStored = Some(true))
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, updatedStatus, updatedTenant)
        awaitForTwoTicks(pocCreation)
        val status = await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds).get
        assertStatusAllTrue(status)
      }
    }
  }

  private def createNeededDeviceUser(users: KeycloakUserService, poc: Poc) = {
    users.createUser(
      DeviceKeycloak.defaultRealm,
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
    status.logoStored shouldBe Some(true)
  }
}
