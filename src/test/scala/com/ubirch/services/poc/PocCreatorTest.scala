package com.ubirch.services.poc
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocLogoRepositoryMock, PocRepositoryMock, PocStatusRepositoryMock, TenantRepositoryMock }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.keycloak.user.CreateKeycloakUser
import com.ubirch.models.poc.{ Completed, LogoURL, Poc, PocStatus, Processing }
import com.ubirch.models.tenant.Tenant
import com.ubirch.models.user.{ Email, FirstName, LastName, UserName }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.keycloak.users.TestKeycloakUserService
import com.ubirch.services.poc.PocTestHelper._
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX

import java.net.URL
import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocCreatorTest extends UnitTestBase {

  "PocCreation" should {

    "create pending poc successfully (less code)" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val groups = injector.get[TestKeycloakGroupsService]
        val users = injector.get[TestKeycloakUserService]
        val keyCloakRoleService = injector.get[KeycloakRolesService]

        //creating needed objects
        val (poc, pocStatus, tenant) = createPocTriple()
        val updatedTenant = createNeededTenantGroups(tenant, groups)
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, updatedTenant)

        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
          DeviceKeycloak).runSyncUnsafe(3.seconds)
        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
          CertifyKeycloak).runSyncUnsafe(3.seconds)

        createNeededDeviceUser(users, poc)
        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        val resultStatus = maybeSuccess.list.head.right.get
        val allTrue = createPocStatusAllTrue()
        val expected =
          allTrue.copy(pocId = poc.id, lastUpdated = resultStatus.lastUpdated, created = resultStatus.created)

        maybeSuccess shouldBe PocCreationMaybeSuccess(List(Right(expected)))

        //val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        //status shouldBe expected

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        assert(newPoc.isDefined)
        assert(newPoc.get.status == Completed)
      }
    }

    "create pending poc successfully with LogoUrl" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocLogoTable = injector.get[PocLogoRepositoryMock]
        val groups = injector.get[TestKeycloakGroupsService]
        val users = injector.get[TestKeycloakUserService]
        val keyCloakRoleService = injector.get[KeycloakRolesService]

        //creating needed objects
        val (poc, pocStatus, tenant) = createPocTriple()
        // This test may be failed in the future when the url is not available.
        val pocWithLogo = poc.copy(logoUrl = Some(LogoURL(
          new URL("https://www.scala-lang.org/resources/img/frontpage/scala-spiral.png"))))
        val pocStatusWithLogoRequired = pocStatus.copy(logoRequired = true, logoStored = Some(false))
        val updatedTenant = createNeededTenantGroups(tenant, groups)
        addPocTripleToRepository(
          tenantTable,
          pocTable,
          pocStatusTable,
          pocWithLogo,
          pocStatusWithLogoRequired,
          updatedTenant)

        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
          DeviceKeycloak).runSyncUnsafe(3.seconds)
        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
          CertifyKeycloak).runSyncUnsafe(3.seconds)

        createNeededDeviceUser(users, poc)
        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        val resultStatus = maybeSuccess.list.head.right.get
        val allTrue = createPocStatusAllTrue()
        val expected =
          allTrue.copy(
            pocId = poc.id,
            logoRequired = true,
            logoStored = Some(true),
            lastUpdated = resultStatus.lastUpdated,
            created = resultStatus.created)

        maybeSuccess shouldBe PocCreationMaybeSuccess(List(Right(expected)))

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        assert(newPoc.isDefined)
        assert(newPoc.get.status == Completed)
        val pocLogo = pocLogoTable.getPocLogoById(poc.id).runSyncUnsafe()
        assert(pocLogo.isDefined)
        assert(pocLogo.get.pocId == poc.id)
      }
    }

    "fail, if user parent group is missing" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val groups = injector.get[TestKeycloakGroupsService]
        val users = injector.get[TestKeycloakUserService]
        val keyCloakRoleService = injector.get[KeycloakRolesService]

        //creating needed objects
        val (poc, pocStatus, tenant) = createPocTriple()
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, tenant)
        createNeededDeviceUser(users, poc)

        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + tenant.tenantName.value)),
          DeviceKeycloak).runSyncUnsafe()
        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + tenant.tenantName.value)),
          CertifyKeycloak).runSyncUnsafe()

        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        maybeSuccess.list.head.isLeft shouldBe true

        //pocStatus should be updated correctly
        val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        val expected = pocStatus.copy(certifyRoleCreated = true, errorMessage = Some("couldn't find parentGroup"))
        status shouldBe expected

        //poc.status should be set to Processing
        pocTable.getPoc(poc.id).runSyncUnsafe(5.seconds).get.status shouldBe Processing

        //fix requirements
        val updatedTenant: Tenant = createNeededTenantGroups(tenant, groups)
        tenantTable.updateTenant(updatedTenant).runSyncUnsafe(5.seconds)

        //start processing again
        val result2 = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess2 = result2.asInstanceOf[PocCreationMaybeSuccess]
        maybeSuccess2.list.head.isRight shouldBe true

        val status2 = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        val expected2 = pocStatus.copy(
          certifyRoleCreated = true,
          certifyGroupCreated = true,
          certifyGroupRoleAssigned = true,
          deviceRoleCreated = true,
          deviceGroupCreated = true,
          deviceGroupRoleAssigned = true,
          deviceCreated = true,
          assignedDataSchemaGroup = true,
          assignedTrustedPocGroup = true,
          assignedDeviceGroup = true,
          certifyApiProvided = true,
          goClientProvided = true
        )

        status2 shouldBe expected2

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe(5.seconds)
        assert(newPoc.isDefined)
        assert(newPoc.get.status == Completed)
      }
    }

    "fail, if the logo url is not image" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val pocLogoTable = injector.get[PocLogoRepositoryMock]
        val groups = injector.get[TestKeycloakGroupsService]
        val users = injector.get[TestKeycloakUserService]
        val keyCloakRoleService = injector.get[KeycloakRolesService]

        //creating needed objects
        val (poc, pocStatus, tenant) = createPocTriple()
        // wrong url which isn't image
        val pocWithLogo = poc.copy(logoUrl = Some(LogoURL(
          new URL("https://www.scala-lang.org"))))
        val pocStatusWithLogoRequired = pocStatus.copy(logoRequired = true, logoStored = Some(false))
        val updatedTenant = createNeededTenantGroups(tenant, groups)
        addPocTripleToRepository(
          tenantTable,
          pocTable,
          pocStatusTable,
          pocWithLogo,
          pocStatusWithLogoRequired,
          updatedTenant)

        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
          DeviceKeycloak).runSyncUnsafe(3.seconds)
        keyCloakRoleService.createNewRole(
          CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
          CertifyKeycloak).runSyncUnsafe(3.seconds)

        createNeededDeviceUser(users, poc)
        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        maybeSuccess.list.head.isLeft shouldBe true

        //pocStatus should be updated correctly
        val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        val allTrue = createPocStatusAllTrue()
        val expected = allTrue.copy(
          pocId = poc.id,
          logoRequired = true,
          logoStored = Some(false),
          errorMessage =
            Some(s"failed to download and store pocLogo: ${poc.id}, LogoURL(https://www.scala-lang.org), The url seems not to be image."),
          lastUpdated = pocStatus.lastUpdated,
          created = pocStatus.created
        )
        status shouldBe expected

        //poc.status should be set to Processing
        pocTable.getPoc(poc.id).runSyncUnsafe(5.seconds).get.status shouldBe Processing

        //fix requirements
        pocTable.getPoc(poc.id).flatMap { poc =>
          val updatedPoc: Poc = poc.get.copy(logoUrl =
            Some(LogoURL(new URL("https://www.scala-lang.org/resources/img/frontpage/scala-spiral.png"))))
          pocTable.updatePoc(updatedPoc)
        }.runSyncUnsafe(5.seconds)

        //start processing again
        val result2 = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess2 = result2.asInstanceOf[PocCreationMaybeSuccess]
        maybeSuccess2.list.head.isRight shouldBe true

        maybeSuccess2 shouldBe PocCreationMaybeSuccess(List(Right(expected.copy(logoStored = Some(true)))))

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        assert(newPoc.isDefined)
        assert(newPoc.get.status == Completed)
        val pocLogo = pocLogoTable.getPocLogoById(poc.id).runSyncUnsafe()
        assert(pocLogo.isDefined)
        assert(pocLogo.get.pocId == poc.id)
      }
    }
  }

}
