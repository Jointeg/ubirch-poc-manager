package com.ubirch.e2e.keycloak

import com.ubirch.db.tables.{ PocRepository, PocStatusRepository, TenantRepository }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc.{ LogoURL, Processing }
import com.ubirch.models.tenant.{ TenantCertifyGroupId, TenantDeviceGroupId }
import com.ubirch.services.poc.PocCreationLoop
import com.ubirch.services.poc.PocTestHelper.{ addPocTripleToRepository, createPocTriple }
import monix.reactive.Observable

import java.net.URL
import scala.concurrent.duration.DurationInt
import scala.util.Random

class ResponseManagementTest extends E2ETestBase {

  "Poc Creation Loop" should {
    "correctly release connections to keycloak if keycloak return error response" in {
      withInjector { injector =>
        val loop = injector.get[PocCreationLoop]
        val tenantTable = injector.get[TenantRepository]
        val pocTable = injector.get[PocRepository]
        val pocStatusTable = injector.get[PocStatusRepository]

        val pocs = (1 to 15).map { _ =>
          val (poc, pocStatus, tenant) = createPocTriple(Random.alphanumeric.take(10).mkString)
          val updatedTenant = tenant.copy(
            deviceGroupId = TenantDeviceGroupId("wrong device group"),
            certifyGroupId = TenantCertifyGroupId("wrong certify group")
          )

          val updatedPoc = poc.copy(logoUrl = Some(LogoURL(
            new URL("https://www.scala-lang.org/resources/img/frontpage/scala-spiral.png"))))
          val updatedStatus = pocStatus.copy(logoRequired = true, logoStored = Some(true))
          addPocTripleToRepository(tenantTable, pocTable, pocStatusTable, updatedPoc, updatedStatus, updatedTenant)

          (poc, pocStatus, updatedTenant)
        }

        awaitForTwoTicks(loop.startPocCreationLoop, 30.seconds)

        // if the connection would not be released properly, than the test would fail here with only 10 PoC being transitioned to Processing state
        pocs.foreach {
          case (poc, _, _) =>
            val updatedPoc = await(pocTable.getPoc(poc.id)).value
            updatedPoc.status shouldBe Processing
        }
      }
    }
  }

}
