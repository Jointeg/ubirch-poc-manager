package com.ubirch.e2e.controllers

import com.ubirch.controllers.SuperAdminController
import com.ubirch.db.tables.TenantRepository
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.keycloak.group.GroupId
import com.ubirch.models.tenant._
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.groups.DefaultKeycloakGroupService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.util.ServiceConstants
import com.ubirch.{ FakeTokenCreator, ModelCreationHelper }
import io.prometheus.client.CollectorRegistry
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

class SuperAdminControllerSpec extends E2ETestBase with BeforeAndAfterEach with BeforeAndAfterAll {

  private def createTenantJson(
    tenantName: String,
    usageType: String = UsageType.APIString,
    tenantType: String = TenantType.UBIRCH_STRING) = {
    s"""
       |{
       |    "tenantName": "$tenantName",
       |    "usageType": "$usageType",
       |    "tenantType": "$tenantType",
       |    "sharedAuthCertRequired": true
       |}
       |""".stripMargin
  }

  private def createTenantJsonWithoutClientCert(tenantName: String) = {
    s"""
       |{
       |    "tenantName": "$tenantName",
       |    "usageType": "API",
       |    "tenantType": "${TenantType.BMG_STRING}",
       |    "sharedAuthCertRequired": false
       |}
       |""".stripMargin
  }

  private def missingRequiredFieldsCreateTenantJson(tenantName: String) = {
    s"""
       |{
       |    "tenantName": "$tenantName",
       |}
       |""".stripMargin
  }

  "Super Admin Controller" must {
    "be able to successfully create a Tenant with sharedAuthCert required" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val groups = injector.get[DefaultKeycloakGroupService]
        val tenantName = getRandomString
        val createTenantBody = createTenantJson(tenantName)
        post(
          "/tenants/create",
          body = createTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(200)
          assert(body == "")
        }

        val tenantRepository = injector.get[TenantRepository]
        val tenant = await(tenantRepository.getTenantByName(TenantName(tenantName)), 2.seconds).get
        tenant.tenantName shouldBe TenantName(tenantName)
        tenant.usageType shouldBe API
        tenant.tenantType shouldBe UBIRCH
        tenant.sharedAuthCert shouldBe Some(SharedAuthCert(ModelCreationHelper.cert))

        val deviceGroup = groups.findGroupById(
          DeviceKeycloak.defaultRealm,
          GroupId(tenant.deviceGroupId.value),
          DeviceKeycloak).runSyncUnsafe()
        assert(deviceGroup.isRight)
        assert(deviceGroup.right.get.getRealmRoles.contains(ServiceConstants.TENANT_GROUP_PREFIX + tenantName))

        val certifyGroup = groups.findGroupById(
          CertifyKeycloak.defaultRealm,
          GroupId(tenant.certifyGroupId.value),
          CertifyKeycloak).runSyncUnsafe()
        assert(certifyGroup.isRight)
        assert(certifyGroup.right.get.getRealmRoles.contains(ServiceConstants.TENANT_GROUP_PREFIX + tenantName))
      }
    }

    "be able to successfully create a Tenant with APP & BMG / BOTH & UBIRCH Type" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val groups = injector.get[DefaultKeycloakGroupService]
        val tenantName = getRandomString
        val ubirchName = "ubirchTenant"
        val bmgName = "bmgTenant"
        val bmgCreateTenantBody = createTenantJson(bmgName, UsageType.APPString, TenantType.BMG_STRING)
        val ubirchCreateTenantBody = createTenantJson(ubirchName, UsageType.BothString, TenantType.UBIRCH_STRING)
        post(
          "/tenants/create",
          body = bmgCreateTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(200)
          assert(body == "")
        }
        post(
          "/tenants/create",
          body = ubirchCreateTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(200)
          assert(body == "")
        }
        val tenantRepository = injector.get[TenantRepository]

        val bmgTenant = await(tenantRepository.getTenantByName(TenantName(bmgName)), 2.seconds).get
        bmgTenant.tenantName shouldBe TenantName(bmgName)
        bmgTenant.usageType shouldBe APP
        bmgTenant.sharedAuthCert shouldBe Some(SharedAuthCert(ModelCreationHelper.cert))
        bmgTenant.tenantType shouldBe BMG
        val bmgGroup =
          groups.findGroupById(
            bmgTenant.getRealm,
            GroupId(bmgTenant.tenantTypeGroupId.get.value),
            CertifyKeycloak).runSyncUnsafe()
        assert(bmgGroup.isRight)
        assert(bmgGroup.right.get.getRealmRoles.contains(ServiceConstants.TENANT_GROUP_PREFIX + bmgName))

        val ubirchTenant = await(tenantRepository.getTenantByName(TenantName(ubirchName)), 2.seconds).get
        ubirchTenant.tenantName shouldBe TenantName(ubirchName)
        ubirchTenant.usageType shouldBe Both
        ubirchTenant.sharedAuthCert shouldBe Some(SharedAuthCert(ModelCreationHelper.cert))
        ubirchTenant.tenantType shouldBe UBIRCH
        val ubirchGroup = groups.findGroupById(
          ubirchTenant.getRealm,
          GroupId(ubirchTenant.tenantTypeGroupId.get.value),
          CertifyKeycloak).runSyncUnsafe()
        assert(ubirchGroup.isRight)
        assert(ubirchGroup.right.get.getRealmRoles.contains(ServiceConstants.TENANT_GROUP_PREFIX + ubirchName))
      }

    }

    "be able to successfully create a Tenant without a sharedAuthCert required" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]

        val tenantName = getRandomString
        val createTenantBody = createTenantJsonWithoutClientCert(tenantName)

        post(
          "/tenants/create",
          body = createTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(200)
          assert(body == "")
        }

        val tenantRepository = injector.get[TenantRepository]
        val maybeTenant = await(tenantRepository.getTenantByName(TenantName(tenantName)), 2.seconds)
        maybeTenant.value.tenantName shouldBe TenantName(tenantName)
        maybeTenant.value.usageType shouldBe API
        maybeTenant.value.sharedAuthCert shouldBe None
      }
    }

    "not be able to create a Tenant with duplicated tenantName" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]

        val tenantName = getRandomString
        val createTenantBody = createTenantJson(tenantName)

        post(
          "/tenants/create",
          body = createTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(200)
          assert(body == "")
        }

        post(
          "/tenants/create",
          body = createTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(500)
          assert(body.contains("Failure during tenant creation"))
        }
      }
    }

    "respond with 400 if request is invalid" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenantName = getRandomString
        val createTenantBody = missingRequiredFieldsCreateTenantJson(tenantName)

        post(
          "/tenants/create",
          body = createTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(400)
          assert(body.contains("Invalid request"))
        }
      }
    }

    "authenticate users related to certify keycloak only" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenantName = getRandomString
        val createTenantBody = createTenantJson(tenantName)

        post(
          "/tenants/create",
          body = createTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdminOnDevicesKeycloak.prepare)) {
          status should equal(403)
          assert(body.contains("AuthenticationError"))
        }

        post(
          "/tenants/create",
          body = createTenantBody.getBytes(StandardCharsets.UTF_8),
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
      lazy val superAdminController = injector.get[SuperAdminController]
      addServlet(superAdminController, "/*")
    }
  }
}
