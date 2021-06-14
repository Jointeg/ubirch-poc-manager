package com.ubirch.e2e.controllers

import com.ubirch.models.poc.{ Completed, LogoURL, Pending, Poc, PocLogo }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.services.formats.{ CustomFormats, JodaDateTimeFormats }
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.poc.employee.GetCertifyConfigDTO
import io.prometheus.client.CollectorRegistry
import org.json4s.{ DefaultFormats, Formats }
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.native.Serialization.read
import com.ubirch.ModelCreationHelper.createPoc
import com.ubirch.controllers.PocEmployeeController
import com.ubirch.db.tables.{ PocLogoRepository, PocTable, TenantTable }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import org.scalatest.BeforeAndAfterEach

import java.io.{ ByteArrayInputStream, File }
import java.net.URL
import java.nio.file.Files
import javax.imageio.ImageIO
import scala.concurrent.duration.DurationInt

class PocEmployeeControllerSpec extends E2ETestBase with BeforeAndAfterEach with ControllerSpecHelper {
  implicit private val formats: Formats =
    DefaultFormats.lossless ++ CustomFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all ++ JodaDateTimeFormats.all

  private val ContentTypeKey = "Content-Type"
  private val JsonContentType = "application/json"

  "Endpoint GET /logo/:pocId" must {
    "return success with png-file ending" in {
      withInjector { Injector =>
        val pocTable = Injector.get[PocTable]
        val pocLogoRepo = Injector.get[PocLogoRepository]
        val tenantTable = Injector.get[TenantTable]
        val tenant = addTenantToDB(Injector)
        val poc = createPoc(tenantName = tenant.tenantName)
          .copy(logoUrl = Some(LogoURL(new URL("http://www.ubirch.com/logo.png"))))

        val logo = addLogoToDB("src/test/resources/img/500_500.png", poc, pocLogoRepo, pocTable)
        logo.isDefined shouldBe true
        val original = ImageIO.read(new ByteArrayInputStream(logo.get.img))

        get(s"/logo/${poc.id.toString}") {
          val response = ImageIO.read(new ByteArrayInputStream(bodyBytes))
          original.getWidth shouldBe response.getWidth
          original.getHeight shouldBe response.getHeight
          header.values.exists(_.startsWith("image/png")) shouldBe true
        }
      }
    }

    "return success with jpeg-file ending" in {
      withInjector { Injector =>
        val pocTable = Injector.get[PocTable]
        val pocLogoRepo = Injector.get[PocLogoRepository]
        val tenantTable = Injector.get[TenantTable]
        val tenant = addTenantToDB(Injector)
        val poc = createPoc(tenantName = tenant.tenantName)
          .copy(logoUrl = Some(LogoURL(new URL("http://www.ubirch.com/logo.jpg"))))

        val logo = addLogoToDB("src/test/resources/img/1024_1024.jpg", poc, pocLogoRepo, pocTable)
        logo.isDefined shouldBe true
        val original = ImageIO.read(new ByteArrayInputStream(logo.get.img))

        get(s"/logo/${poc.id.toString}") {
          val response = ImageIO.read(new ByteArrayInputStream(bodyBytes))
          original.getWidth shouldBe response.getWidth
          original.getHeight shouldBe response.getHeight
          header.values.exists(_.startsWith("image/jpeg")) shouldBe true
        }
      }
    }
  }

  "Endpoint GET /certify-config" should {
    val EndPoint = "/certify-config"
    def configEndpoint(poc: Poc) = EndPoint + s"/${poc.roleName}"
    "get certify config UBIRCH vaccination centers" in {
      withInjector { injector =>
        val tenant = addTenantToDB(injector)
        val keycloakRoleService = injector.get[KeycloakRolesService]
        val pocTable = injector.get[PocTable]
        val poc =
          createPoc(tenantName = tenant.tenantName, status = Pending).copy(pocType = "ub_vac_app", status = Completed)
        val r =
          for {
            _ <- pocTable.createPoc(poc)
          } yield ()
        await(r, 5.seconds)
        get(configEndpoint(poc)) {
          status should equal(200)
          header.get(ContentTypeKey).foreach { contentType =>
            contentType shouldBe JsonContentType + ";charset=utf-8"
          }
          val certifyConfig = read[GetCertifyConfigDTO](body)
          certifyConfig.pocId shouldBe poc.externalId
          certifyConfig.pocName shouldBe poc.pocName
          certifyConfig.logoUrl shouldBe "https://api.dev.ubirch.com/poc-employee/logo/" + poc.id.toString
          certifyConfig.styleTheme shouldBe Some("theme-blue")
          certifyConfig.dataSchemaSettings.length shouldBe 1
          certifyConfig.dataSchemaSettings.head.dataSchemaId shouldBe "vaccination-v3"
          certifyConfig.dataSchemaSettings.head.packagingFormat shouldBe Some("UPP")
        }
      }
    }

    "get certify config BMG vaccination center POC" in {
      withInjector { injector =>
        val tenant = addTenantToDB(injector)
        val pocTable = injector.get[PocTable]
        val poc =
          createPoc(tenantName = tenant.tenantName, status = Pending).copy(pocType = "bmg_vac_app", status = Completed)
        val r =
          for {
            _ <- pocTable.createPoc(poc)
          } yield ()
        await(r, 5.seconds)
        get(configEndpoint(poc)) {
          status should equal(200)
          val certifyConfig = read[GetCertifyConfigDTO](body)
          certifyConfig.pocId shouldBe poc.externalId
          certifyConfig.pocName shouldBe "Robert Koch-Institut"
          certifyConfig.logoUrl shouldBe "https://api.dev.ubirch.com/poc-employee/logo/" + poc.id.toString
          certifyConfig.styleTheme shouldBe Some("theme-bmg-blue")
          certifyConfig.dataSchemaSettings.length shouldBe 2
          certifyConfig.dataSchemaSettings.head.dataSchemaId shouldBe "vaccination-bmg-v2"
          certifyConfig.dataSchemaSettings.head.packagingFormat shouldBe Some("CBOR")
          certifyConfig.dataSchemaSettings(1).dataSchemaId shouldBe "recovery-bmg"
          certifyConfig.dataSchemaSettings(1).packagingFormat shouldBe Some("CBOR")
        }
      }
    }

    "get certify config BVDW POC" in {
      withInjector { injector =>
        val tenant = addTenantToDB(injector)
        val pocTable = injector.get[PocTable]
        val poc =
          createPoc(tenantName = tenant.tenantName, status = Pending).copy(pocType = "ub_cust_app", status = Completed)
        val r =
          for {
            _ <- pocTable.createPoc(poc)
          } yield ()
        await(r, 5.seconds)
        get(configEndpoint(poc)) {
          status should equal(200)
          val certifyConfig = read[GetCertifyConfigDTO](body)
          certifyConfig.pocId shouldBe poc.externalId
          certifyConfig.pocName shouldBe poc.pocName
          certifyConfig.logoUrl shouldBe "https://api.dev.ubirch.com/poc-employee/logo/" + poc.id.toString
          certifyConfig.styleTheme shouldBe None
          certifyConfig.dataSchemaSettings.length shouldBe 1
          certifyConfig.dataSchemaSettings.head.dataSchemaId shouldBe "bvdw-certificate"
          certifyConfig.dataSchemaSettings.head.packagingFormat shouldBe Some("UPP")
        }
      }
    }

    "return BadRequest when pocType is wrong" in {
      withInjector { injector =>
        val tenant = addTenantToDB(injector)
        val pocTable = injector.get[PocTable]
        val poc =
          createPoc(tenantName = tenant.tenantName, status = Pending).copy(pocType = "ub_cust_app1", status = Completed)
        val r =
          for {
            _ <- pocTable.createPoc(poc)
          } yield ()
        await(r, 5.seconds)
        get(configEndpoint(poc)) {
          status should equal(400)
        }
      }
    }

    "return Bad Request when pocRole has a wrong format" in {
      get(EndPoint + "/test") {
        status should equal(400)
      }
    }

    "return Bad Request when pocRole doesn't include uuid" in {
      get(EndPoint + "/test_test_1232454") {
        status should equal(400)
      }
    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    withInjector { injector =>
      lazy val pocEmployeeController = injector.get[PocEmployeeController]
      addServlet(pocEmployeeController, "/*")
    }
  }

  private def addLogoToDB(
    pathName: String,
    poc: Poc,
    pocLogoTable: PocLogoRepository,
    pocTable: PocTable): Option[PocLogo] = {
    val file = new File(pathName)
    val imgBytes = Files.readAllBytes(file.toPath)

    (for {
      _ <- pocTable.createPoc(poc)
      pocLogo <- PocLogo.create(poc.id, imgBytes).map(_.right.get)
      _ <- pocLogoTable.createPocLogo(pocLogo)
      pocLogoFromDB <- pocLogoTable.getPocLogoById(poc.id)
    } yield pocLogoFromDB).runSyncUnsafe()
  }
}
