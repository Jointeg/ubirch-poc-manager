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
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import com.ubirch.{ UnitTestBase, UnitTestInjectorHelper }

import scala.concurrent.duration.DurationInt

class PocCreatorCertificateCreationTest extends UnitTestBase {

  "PocCreator" should {
    "Retrieve certificates from CertManager and mark this information in PocStatus when tenant UsageType == APP and clientCertRequired == true" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]

        val (poc, pocStatus, _) = createPocWithStatusAndTenant(injector)(
          pocChange = poc => poc.copy(clientCertRequired = true),
          tenantChange = tenant => tenant.copy(usageType = APP))
        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        val resultStatus = maybeSuccess.list.head.right.get
        val allTrue = createPocStatusAllTrue()
        val expected =
          allTrue.copy(
            pocId = poc.id,
            lastUpdated = resultStatus.lastUpdated,
            created = resultStatus.created,
            clientCertCreated = Some(true),
            orgUnitCertCreated = Some(true)
          )

        maybeSuccess shouldBe PocCreationMaybeSuccess(List(Right(expected)))

        val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        status shouldBe expected

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        assert(newPoc.isDefined)
        assert(newPoc.get.status == Completed)
      }
    }

    "Retrieve certificates from CertManager and mark this information in PocStatus when tenant UsageType == Both and clientCertRequired == true" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]

        val (poc, pocStatus, _) = createPocWithStatusAndTenant(injector)(
          pocChange = poc => poc.copy(clientCertRequired = true),
          tenantChange = tenant => tenant.copy(usageType = Both))
        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        val resultStatus = maybeSuccess.list.head.right.get
        val allTrue = createPocStatusAllTrue()
        val expected =
          allTrue.copy(
            pocId = poc.id,
            lastUpdated = resultStatus.lastUpdated,
            created = resultStatus.created,
            clientCertCreated = Some(true),
            orgUnitCertCreated = Some(true)
          )

        maybeSuccess shouldBe PocCreationMaybeSuccess(List(Right(expected)))

        val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        status shouldBe expected

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        assert(newPoc.isDefined)
        assert(newPoc.get.status == Completed)
      }
    }

    List(API, APP, Both).foreach(usageType => {
      s"Do not retrieve certificates from CertManager and when tenant UsageType == $usageType and clientCertRequired == false" in {
        withInjector { injector =>
          //services
          val loop = injector.get[PocCreator]
          val pocTable = injector.get[PocRepositoryMock]
          val pocStatusTable = injector.get[PocStatusRepositoryMock]

          val (poc, pocStatus, _) = createPocWithStatusAndTenant(injector)(
            pocChange = poc => poc.copy(clientCertRequired = false),
            tenantChange = tenant => tenant.copy(usageType = usageType))
          //start process
          val result = await(loop.createPocs(), 5.seconds)

          //validate result
          val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
          val resultStatus = maybeSuccess.list.head.right.get
          val allTrue = createPocStatusAllTrue()
          val expected =
            allTrue.copy(
              pocId = poc.id,
              lastUpdated = resultStatus.lastUpdated,
              created = resultStatus.created,
              clientCertCreated = None,
              orgUnitCertCreated = None
            )

          maybeSuccess shouldBe PocCreationMaybeSuccess(List(Right(expected)))

          val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
          status shouldBe expected

          val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
          assert(newPoc.isDefined)
          assert(newPoc.get.status == Completed)
        }
      }
    })

    "Fail to complete the creation of PoC if clientCertRequired is set to true but tenant UsageType is API" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]

        val (poc, pocStatus, _) = createPocWithStatusAndTenant(injector)(
          pocChange = poc => poc.copy(clientCertRequired = true),
          tenantChange = tenant => tenant.copy(usageType = API))
        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        val resultStatus = maybeSuccess.list.head.left.get
        assert(resultStatus.contains("error: a poc shouldn't require client cert if tenant usageType is API"))

        val status = pocStatusTable.getPocStatus(pocStatus.pocId).runSyncUnsafe(5.seconds).get
        status.errorMessage shouldBe Some("a poc shouldn't require client cert if tenant usageType is API")

        val newPoc = pocTable.getPoc(poc.id).runSyncUnsafe()
        assert(newPoc.isDefined)
        assert(newPoc.get.status == Processing)
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
      CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
      DeviceKeycloak).runSyncUnsafe(3.seconds)
    keyCloakRoleService.createNewRole(
      CreateKeycloakRole(RoleName(TENANT_GROUP_PREFIX + updatedTenant.tenantName.value)),
      CertifyKeycloak).runSyncUnsafe(3.seconds)

    createNeededDeviceUser(users, pocChanged)

    (pocChanged, pocStatusChanged, updatedTenant)
  }

}
