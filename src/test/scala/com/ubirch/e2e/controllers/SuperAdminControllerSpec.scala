package com.ubirch.e2e.controllers

import com.ubirch.FakeTokenCreator
import com.ubirch.controllers.SuperAdminController
import com.ubirch.e2e.E2ETestBase
import com.ubirch.services.jwt.PublicKeyPoolService
import io.prometheus.client.CollectorRegistry
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class SuperAdminControllerSpec extends E2ETestBase with BeforeAndAfterEach with BeforeAndAfterAll {

  "Super Admin Controller" must {
    "should be able to successfully create a Tenant" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]

        val requestBody =
          s"""
            |{
            |    "tenantName": "someRandomName",
            |    "pocUsageBase": "APIUsage",
            |    "deviceCreationToken": "1234567890",
            |    "certificationCreationToken": "987654321",
            |    "idGardIdentifier": "gard-identifier",
            |    "tenantGroupId": "random-group",
            |    "tenantOrganisationalUnitGroupId": "tenantOrganisationalUnitGroupId"
            |}
            |""".stripMargin
        post(
          "/tenants/create",
          body = requestBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(200)
          assert(body == "")
        }
      }
    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    withInjector { injector =>
      lazy val pool = injector.get[PublicKeyPoolService]
      await(pool.init, 2 seconds)

      lazy val superAdminController = injector.get[SuperAdminController]
      addServlet(superAdminController, "/*")
    }
  }
}
