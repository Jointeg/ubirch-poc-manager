package com.ubirch.e2e.controllers

import com.ubirch.FakeTokenCreator
import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createPocEmployee }
import com.ubirch.controllers.PocEmployeeController
import com.ubirch.db.tables.{ PocAdminTable, PocEmployeeTable, PocTable }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc.Pending
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.services.formats.{ CustomFormats, JodaDateTimeFormats }
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.poc.employee.GetCertifyConfigDTO
import io.prometheus.client.CollectorRegistry
import org.json4s.{ DefaultFormats, Formats }
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.scalatest.BeforeAndAfterEach
import org.json4s.native.Serialization.read

import java.util.UUID
import scala.concurrent.duration.DurationInt

class PocEmployeeControllerSpec extends E2ETestBase with BeforeAndAfterEach with ControllerSpecHelper {
  implicit private val formats: Formats =
    DefaultFormats.lossless ++ CustomFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all ++ JodaDateTimeFormats.all

  private val ContentTypeKey = "Content-Type"
  private val JsonContentType = "application/json"

  "Endpoint GET /certify-config" should {
    val EndPoint = "/certify-config"
    "get certify config UBIRCH vaccination centers" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val employeeTable = injector.get[PocEmployeeTable]
        val (tenant, poc, _) = createTenantWithPocAndPocAdmin(injector)
        val employee =
          createPocEmployee(pocId = poc.id, tenantId = tenant.id).copy(certifyUserId = Some(UUID.randomUUID()))
        await(employeeTable.createPocEmployee(employee), 5.seconds)
        get(EndPoint, headers = Map("authorization" -> token.pocEmployee(employee.certifyUserId.value).prepare)) {
          status should equal(200)
          header.get(ContentTypeKey).foreach { contentType =>
            contentType shouldBe JsonContentType + ";charset=utf-8"
          }
          val certifyConfig = read[GetCertifyConfigDTO](body)
          certifyConfig.pocId shouldBe poc.id.toString
          certifyConfig.pocName shouldBe poc.pocName
          certifyConfig.logoUrl shouldBe "https://api.dev.ubirch.com/poc-employee/logo/" + poc.id.toString
          certifyConfig.styleTheme shouldBe Some("theme-blue")
          certifyConfig.dataSchemaSettings.dataSchemaId shouldBe "vaccination-v3"
          certifyConfig.dataSchemaSettings.packagingFormat shouldBe Some("UPP")
        }
      }
    }

    "get certify config BMG vaccination center POC" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenant = addTenantToDB(injector)
        val employeeTable = injector.get[PocEmployeeTable]
        val pocTable = injector.get[PocTable]
        val pocAdminTable = injector.get[PocAdminTable]
        val poc = createPoc(tenantName = tenant.tenantName, status = Pending).copy(pocType = "bmg_vac_app")
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val employee =
          createPocEmployee(pocId = poc.id, tenantId = tenant.id).copy(certifyUserId = Some(UUID.randomUUID()))
        val r =
          for {
            _ <- pocTable.createPoc(poc)
            _ <- pocAdminTable.createPocAdmin(pocAdmin)
            _ <- employeeTable.createPocEmployee(employee)
          } yield ()
        await(r, 5.seconds)
        get(EndPoint, headers = Map("authorization" -> token.pocEmployee(employee.certifyUserId.value).prepare)) {
          status should equal(200)
          val certifyConfig = read[GetCertifyConfigDTO](body)
          certifyConfig.pocId shouldBe poc.id.toString
          certifyConfig.pocName shouldBe poc.pocName
          certifyConfig.logoUrl shouldBe "https://api.dev.ubirch.com/poc-employee/logo/" + poc.id.toString
          certifyConfig.styleTheme shouldBe Some("theme-bmg-blue")
          certifyConfig.dataSchemaSettings.dataSchemaId shouldBe "vaccination-bmg-v2"
          certifyConfig.dataSchemaSettings.packagingFormat shouldBe Some("CBOR")
        }
      }
    }

    "get certify config BVDW POC" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenant = addTenantToDB(injector)
        val employeeTable = injector.get[PocEmployeeTable]
        val pocTable = injector.get[PocTable]
        val pocAdminTable = injector.get[PocAdminTable]
        val poc = createPoc(tenantName = tenant.tenantName, status = Pending).copy(pocType = "ub_cust_app")
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val employee =
          createPocEmployee(pocId = poc.id, tenantId = tenant.id).copy(certifyUserId = Some(UUID.randomUUID()))
        val r =
          for {
            _ <- pocTable.createPoc(poc)
            _ <- pocAdminTable.createPocAdmin(pocAdmin)
            _ <- employeeTable.createPocEmployee(employee)
          } yield ()
        await(r, 5.seconds)
        get(EndPoint, headers = Map("authorization" -> token.pocEmployee(employee.certifyUserId.value).prepare)) {
          status should equal(200)
          val certifyConfig = read[GetCertifyConfigDTO](body)
          certifyConfig.pocId shouldBe poc.id.toString
          certifyConfig.pocName shouldBe poc.pocName
          certifyConfig.logoUrl shouldBe "https://api.dev.ubirch.com/poc-employee/logo/" + poc.id.toString
          certifyConfig.styleTheme shouldBe None
          certifyConfig.dataSchemaSettings.dataSchemaId shouldBe "bvdw-certificate"
          certifyConfig.dataSchemaSettings.packagingFormat shouldBe Some("UPP")
        }
      }
    }

    "return BadRequest when pocType is wrong" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenant = addTenantToDB(injector)
        val employeeTable = injector.get[PocEmployeeTable]
        val pocTable = injector.get[PocTable]
        val pocAdminTable = injector.get[PocAdminTable]
        val poc = createPoc(tenantName = tenant.tenantName, status = Pending).copy(pocType = "ub_cust_app1")
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val employee =
          createPocEmployee(pocId = poc.id, tenantId = tenant.id).copy(certifyUserId = Some(UUID.randomUUID()))
        val r =
          for {
            _ <- pocTable.createPoc(poc)
            _ <- pocAdminTable.createPocAdmin(pocAdmin)
            _ <- employeeTable.createPocEmployee(employee)
          } yield ()
        await(r, 5.seconds)
        get(EndPoint, headers = Map("authorization" -> token.pocEmployee(employee.certifyUserId.value).prepare)) {
          status should equal(400)
        }
      }
    }

    "return Bad Request when employee doesn't exist" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        get(EndPoint, headers = Map("authorization" -> token.pocEmployee(UUID.randomUUID()).prepare)) {
          status should equal(404)
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
      await(pool.init(DeviceKeycloak, CertifyKeycloak), 2.seconds)

      lazy val superAdminController = injector.get[PocEmployeeController]
      addServlet(superAdminController, "/*")
    }
  }
}
