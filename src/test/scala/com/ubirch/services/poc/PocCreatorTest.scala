package com.ubirch.services.poc
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocRepositoryMock, PocStatusRepositoryMock, TenantRepositoryMock }
import com.ubirch.models.poc.PocStatus
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.poc.PocTestHelper._

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocCreatorTest extends UnitTestBase {

  "PocCreationLoop" should {

    "create pending poc successfully (less code)" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
        val tenantTable = injector.get[TenantRepositoryMock]
        val pocTable = injector.get[PocRepositoryMock]
        val pocStatusTable = injector.get[PocStatusRepositoryMock]
        val groups = injector.get[TestKeycloakGroupsService]

        //creating needed objects
        val (poc, pocStatus, tenant) = createPocTriple()
        val updatedTenant = createNeededTenantGroups(tenant, groups)
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, updatedTenant)

        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        val resultStatus = maybeSuccess.list.head.right.get
        val allTrue = createPocStatusAllTrue()
        val expected =
          allTrue.copy(pocId = poc.id, lastUpdated = resultStatus.lastUpdated, created = resultStatus.created)

        maybeSuccess shouldBe PocCreationMaybeSuccess(List(Right(expected)))

        val status = await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds).get
        status shouldBe expected
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

        //creating needed objects
        val (poc, pocStatus, tenant) = createPocTriple()
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, tenant)

        //start process
        val result = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess = result.asInstanceOf[PocCreationMaybeSuccess]
        maybeSuccess.list.head.isLeft shouldBe true

        val status = await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds).get
        val expected = pocStatus.copy(
          userRoleCreated = true,
          errorMessage = Some("couldn't find parentGroup"))

        status shouldBe expected

        val updatedTenant: Tenant = createNeededTenantGroups(tenant, groups)
        await(tenantTable.updateTenant(updatedTenant), 1.seconds)

        //start process again
        val result2 = await(loop.createPocs(), 5.seconds)

        //validate result
        val maybeSuccess2 = result2.asInstanceOf[PocCreationMaybeSuccess]
        maybeSuccess2.list.head.isRight shouldBe true

        val status2 = await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds).get
        val expected2 = pocStatus.copy(
          userRoleCreated = true,
          userGroupCreated = true,
          userGroupRoleAssigned = true,
          deviceRoleCreated = true,
          deviceGroupCreated = true,
          deviceGroupRoleAssigned = true,
          deviceCreated = true,
          certifyApiProvided = true,
          goClientProvided = true,
          errorMessage = Some("couldn't find parentGroup")
        )

        status2 shouldBe expected2
      }
    }
  }

  private def createPocStatusAllTrue() = {

    PocStatus(
      UUID.randomUUID(),
      validDataSchemaGroup = true,
      userRoleCreated = true,
      userGroupCreated = true,
      userGroupRoleAssigned = true,
      deviceRoleCreated = true,
      deviceGroupCreated = true,
      deviceGroupRoleAssigned = true,
      deviceCreated = true,
      clientCertRequired = false,
      None,
      None,
      logoRequired = false,
      None,
      None,
      certifyApiProvided = true,
      goClientProvided = true,
      None
    )
  }

}
