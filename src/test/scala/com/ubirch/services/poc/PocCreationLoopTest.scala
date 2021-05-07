package com.ubirch.services.poc
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocStatusTestTable, PocTestTable, TenantTestTable }
import com.ubirch.models.poc.PocStatus
import com.ubirch.services.keycloak.groups.TestKeycloakGroupsService
import com.ubirch.services.poc.PocTestHelper._
import monix.reactive.Observable
import org.scalatest.Assertion

import scala.concurrent.duration.DurationInt

class PocCreationLoopTest extends UnitTestBase {

  "Poc Creation Loop" should {
    "create pending poc first after adding it to database" in {
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

        //start process
        val pocCreation = loop.startPocCreationLoop(resp => Observable(resp)).subscribe()
        Thread.sleep(4000)
        await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds) shouldBe None
        addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, poc, pocStatus, updatedTenant)
        Thread.sleep(2000)
        val status = await(pocStatusTable.getPocStatus(pocStatus.pocId), 1.seconds).get
        assertStatusAllTrue(status)
        pocCreation.cancel()
      }
    }
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
