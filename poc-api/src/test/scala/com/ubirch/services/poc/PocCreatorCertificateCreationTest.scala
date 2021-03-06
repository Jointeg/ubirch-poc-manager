package com.ubirch.services.poc

import com.ubirch.db.tables.{ PocRepositoryMock, PocStatusRepositoryMock, TenantRepositoryMock }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleName }
import com.ubirch.models.poc.{ Completed, Poc, PocStatus, Processing }
import com.ubirch.models.tenant.{ API, APP, Both, Tenant }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.keycloak.users.TestKeycloakUserService
import com.ubirch.services.poc.PocTestHelper._
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.test.TestData
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import com.ubirch.{ UnitTestBase, UnitTestInjectorHelper }

import scala.concurrent.duration.DurationInt

class PocCreatorCertificateCreationTest extends UnitTestBase {

  "PocCreator" should {
    "Retrieve certificates from CertManager and mark this information in PocStatus and PoC table when tenant UsageType == APP" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]

        val (poc, pocStatus, _) = createPocWithStatusAndTenant(injector)(
          pocChange = poc => poc.copy(pocType = TestData.Poc.pocTypeUbVacApp),
          pocStatusChange = pocStatus => pocStatus.copy(clientCertRequired = true, clientCertCreated = Some(false)),
          tenantChange = tenant => tenant.copy(usageType = APP)
        )
        //start process
        await(loop.createPocs(), 5.seconds)

        val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        val allTrue = createPocStatusAllTrue()
        val expected =
          allTrue.copy(
            pocId = poc.id,
            lastUpdated = status.lastUpdated,
            created = status.created,
            clientCertRequired = true,
            clientCertCreated = Some(true),
            clientCertProvided = Some(true),
            orgUnitCertCreated = Some(true)
          )
        status shouldBe expected

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        newPoc.value.status shouldBe Completed
        newPoc.value.sharedAuthCertId shouldBe defined
      }
    }

    "Retrieve certificates from CertManager and mark this information in PocStatus when tenant UsageType == Both and pocType is APP" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]

        val (poc, pocStatus, _) = createPocWithStatusAndTenant(injector)(
          pocChange = poc => poc.copy(pocType = TestData.Poc.pocTypeUbVacApp),
          pocStatusChange = pocStatus => pocStatus.copy(clientCertRequired = true, clientCertCreated = Some(false)),
          tenantChange = tenant => tenant.copy(usageType = Both)
        )
        //start process
        await(loop.createPocs(), 5.seconds)

        val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        val allTrue = createPocStatusAllTrue()
        val expected =
          allTrue.copy(
            pocId = poc.id,
            lastUpdated = status.lastUpdated,
            created = status.created,
            clientCertRequired = true,
            clientCertCreated = Some(true),
            clientCertProvided = Some(true),
            orgUnitCertCreated = Some(true)
          )
        status shouldBe expected

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        newPoc.value.status shouldBe Completed
        newPoc.value.sharedAuthCertId shouldBe defined
      }
    }

    List(API, APP, Both).foreach(usageType => {
      s"Do not retrieve certificates from CertManager and when tenant UsageType == $usageType and pocType is API" in {
        withInjector { injector =>
          //services
          val loop = injector.get[PocCreator]
          val pocTable = injector.get[PocRepositoryMock]
          val pocStatusTable = injector.get[PocStatusRepositoryMock]

          val (poc, pocStatus, _) = createPocWithStatusAndTenant(injector)(
            pocChange = poc => poc.copy(pocType = TestData.Poc.pocTypeBmgVacApi),
            tenantChange = tenant => tenant.copy(usageType = usageType))
          //start process
          await(loop.createPocs(), 5.seconds)

          val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
          val allTrue = createPocStatusAllTrue()
          val expected =
            allTrue.copy(
              pocId = poc.id,
              lastUpdated = status.lastUpdated,
              created = status.created,
              clientCertCreated = None,
              orgUnitCertCreated = None
            )
          status shouldBe expected

          val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
          newPoc.value.status shouldBe Completed
          newPoc.value.sharedAuthCertId shouldNot be(defined)
        }
      }
    })

    "Fail to complete the creation of PoC if clientCertRequired is set to true but tenant UsageType is API and pocType is APP" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]

        val (poc, pocStatus, _) = createPocWithStatusAndTenant(injector)(
          pocChange = poc => poc.copy(pocType = TestData.Poc.pocTypeUbVacApp),
          tenantChange = tenant => tenant.copy(usageType = API))
        //start process
        await(loop.createPocs(), 5.seconds)

        val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        status.errorMessage shouldBe Some("a poc shouldn't require client cert if tenant usageType is API")

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        newPoc.value.status shouldBe Processing
        newPoc.value.sharedAuthCertId shouldNot be(defined)
      }
    }
  }

  private def createPocWithStatusAndTenant(injector: UnitTestInjectorHelper)(
    pocChange: Poc => Poc = identity,
    pocStatusChange: PocStatus => PocStatus = identity,
    tenantChange: Tenant => Tenant = identity): (Poc, PocStatus, Tenant) = {
    val tenantTable = injector.get[TenantRepositoryMock]
    val pocTable = injector.get[PocRepositoryMock]
    val pocStatusTable = injector.get[PocStatusRepositoryMock]
    val groups = injector.get[TestKeycloakGroupsService]
    val users = injector.get[TestKeycloakUserService]
    val keyCloakRoleService = injector.get[KeycloakRolesService]

    //creating needed objects
    val (poc, pocStatus, tenant) = createPocTriple()
    val pocChanged = pocChange(poc)
    val pocStatusChanged = pocStatusChange(pocStatus)
    val tenantChanged = tenantChange(tenant)
    val updatedTenant = createNeededTenantGroups(tenantChanged, groups)
    addPocTripleToRepository(
      tenantTable,
      pocTable,
      pocStatusTable,
      pocChanged,
      pocStatusChanged,
      updatedTenant)

    keyCloakRoleService.createNewRole(
      DeviceKeycloak.defaultRealm,
      CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
      DeviceKeycloak).runSyncUnsafe(3.seconds)
    keyCloakRoleService.createNewRole(
      CertifyKeycloak.defaultRealm,
      CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
      CertifyKeycloak).runSyncUnsafe(3.seconds)

    createNeededDeviceUser(users, pocChanged)

    (pocChanged, pocStatusChanged, updatedTenant)
  }

}
