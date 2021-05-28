package com.ubirch.e2e.controllers

import com.ubirch.ModelCreationHelper.{ createPoc, createTenant }
import com.ubirch.controllers.PocEmployeeController
import com.ubirch.db.tables.{ PocLogoRepository, PocTable, TenantTable }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc.{ LogoURL, Poc, PocLogo }
import com.ubirch.models.tenant.Tenant
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import java.io.{ ByteArrayInputStream, File }
import java.net.URL
import java.nio.file.Files
import javax.imageio.ImageIO
import scala.concurrent.duration.DurationInt

class PocEmployeeControllerSpec extends E2ETestBase with BeforeAndAfterEach with BeforeAndAfterAll {

  "Endpoint GET /logo/:pocId" must {
    "return success with png-file ending" in {
      withInjector { Injector =>
        val pocTable = Injector.get[PocTable]
        val pocLogoRepo = Injector.get[PocLogoRepository]
        val tenantTable = Injector.get[TenantTable]
        val tenant = addTenantToDB(tenantTable)
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
        val tenant = addTenantToDB(tenantTable)
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

  private def addTenantToDB(tenantTable: TenantTable): Tenant = {
    val tenant = createTenant()
    await(tenantTable.createTenant(tenant), 5.seconds)
    tenant
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

  override protected def beforeAll: Unit = {
    super.beforeAll()
    withInjector { injector =>
      lazy val pocEmployeeController = injector.get[PocEmployeeController]
      addServlet(pocEmployeeController, "/*")
    }
  }

}
