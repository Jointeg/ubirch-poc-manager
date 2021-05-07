package com.ubirch.services.poc
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocStatusTestTable, PocTestTable, TenantTestTable }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.poc.PocTestHelper._

import scala.concurrent.duration.DurationInt

class PocCreatorTest extends UnitTestBase {

  "PocCreationLoop" should {
    "create pending poc successfully" in {
      withInjector { injector =>
        //services
        val loop = injector.get[PocCreator]
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

}
