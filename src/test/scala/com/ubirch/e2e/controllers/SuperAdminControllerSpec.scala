package com.ubirch.e2e.controllers

import com.ubirch.FakeTokenCreator
import com.ubirch.controllers.SuperAdminController
import com.ubirch.db.tables.TenantRepository
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.auth.{Base64String, EncryptedData}
import com.ubirch.models.tenant._
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.jwt.PublicKeyPoolService
import io.prometheus.client.CollectorRegistry
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Random

class SuperAdminControllerSpec extends E2ETestBase with BeforeAndAfterEach with BeforeAndAfterAll {

  private def createTenantJson(tenantName: String) = {
    s"""
      |{
      |    "tenantName": "$tenantName",
      |    "usageType": "API",
      |    "deviceCreationToken": "1234567890",
      |    "certificationCreationToken": "987654321",
      |    "idGardIdentifier": "gard-identifier",
      |    "tenantGroupId": "random-group",
      |    "tenantOrganisationalUnitGroupId": "organisationalUnitGroupId"
      |}
      |""".stripMargin
  }

  private def missingRequiredFieldsCreateTenantJson(tenantName: String) = {
    s"""
      |{
      |    "tenantName": "$tenantName",
      |    "deviceCreationToken": "1234567890",
      |    "idGardIdentifier": "gard-identifier",
      |    "tenantGroupId": "random-group",
      |    "tenantOrganisationalUnitGroupId": "organisationalUnitGroupId"
      |}
      |""".stripMargin
  }

  "Super Admin Controller" must {
    "be able to successfully create a Tenant" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]

        val tenantName = Random.alphanumeric.take(10).mkString
        val createTenantBody = createTenantJson(tenantName)

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
        maybeTenant.value.idGardIdentifier shouldBe IdGardIdentifier("gard-identifier")
        maybeTenant.value.groupId shouldBe TenantGroupId("random-group")
        maybeTenant.value.organisationalUnitGroupId shouldBe TenantOrganisationalUnitGroupId(
          "organisationalUnitGroupId")
      }
    }

    "create tenant encrypted DeviceCreationToken and CertificationCreationToken" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenantName = Random.alphanumeric.take(10).mkString
        val createTenantBody = createTenantJson(tenantName)

        post(
          "/tenants/create",
          body = createTenantBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(200)
          assert(body == "")
        }

        val tenantRepository = injector.get[TenantRepository]
        val maybeTenant = await(tenantRepository.getTenantByName(TenantName(tenantName)), 2.seconds)

        maybeTenant.value.deviceCreationToken shouldNot be(
          EncryptedDeviceCreationToken(EncryptedData(Base64String.toBase64String("1234567890"))))
        maybeTenant.value.certificationCreationToken shouldNot be(
          EncryptedCertificationCreationToken(EncryptedData(Base64String.toBase64String("987654321"))))

        val aesEncryption = injector.get[AESEncryption]
        val decryptedDeviceCreationToken =
          await(aesEncryption.decrypt(maybeTenant.value.deviceCreationToken.value)(_.value), 1.second)
        val decryptedCertificationCreationToken =
          await(aesEncryption.decrypt(maybeTenant.value.certificationCreationToken.value)(_.value), 1.second)

        decryptedDeviceCreationToken shouldBe "1234567890"
        decryptedCertificationCreationToken shouldBe "987654321"

      }
    }

    "respond with 500 status code if some error happens" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenantName = Random.alphanumeric.take(10).mkString
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
