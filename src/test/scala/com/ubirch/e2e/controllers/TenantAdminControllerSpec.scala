package com.ubirch.e2e.controllers

import com.ubirch.FakeTokenCreator
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.controllers.TenantAdminController
import com.ubirch.controllers.TenantAdminController.PoC_OUT
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository, TenantTable }
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.ValidationErrorsResponse
import com.ubirch.models.poc.{ Completed, Pending, PocStatus, Processing }
import com.ubirch.models.tenant.{ Tenant, TenantName }
import com.ubirch.services.formats.{ DomainObjectFormats, JodaDateTimeFormats }
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.poc.util.CsvConstants
import com.ubirch.services.poc.util.CsvConstants.headerLine
import com.ubirch.services.{ DeviceKeycloak, UsersKeycloak }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import io.prometheus.client.CollectorRegistry
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.native.Serialization.{ read, write }
import org.json4s.{ DefaultFormats, Formats }
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import java.util.UUID
import scala.concurrent.duration.DurationInt

class TenantAdminControllerSpec
  extends E2ETestBase
  with TableDrivenPropertyChecks
  with BeforeAndAfterEach
  with BeforeAndAfterAll {

  private val poc1id: UUID = UUID.randomUUID()
  private val poc2id: UUID = UUID.randomUUID()
  implicit private val formats: Formats =
    DefaultFormats.lossless ++ DomainObjectFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all ++ JodaDateTimeFormats.all

  private val badCsv =
    "poc_id*;poc_name*;poc_street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;client_cert;data_schema_id*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12A636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12A636;Wunschstadt;;;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}"

  private val goodCsv =
    s"""$headerLine
       |${poc1id.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  "Endpoint POST pocs/create" must {
    "return success without invalid rows" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val tenant = addTenantToDB()
        post(
          "/pocs/create",
          body = goodCsv.getBytes(),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          assert(body.isEmpty)
        }
        val repo = Injector.get[PocRepository]
        val pocs = await(repo.getAllPocsByTenantId(tenant.id), 5.seconds)
        pocs.map(_.externalId shouldBe poc1id.toString)
      }
    }

    "return Forbidden when user is not a tenant-admin" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        addTenantToDB()
        post("/pocs/create", body = badCsv.getBytes(), headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(403)
          assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
        }
      }
    }

    "return Bad Request when tenant doesn't exist" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        post(
          "/pocs/create",
          body = badCsv.getBytes(),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName("tenantName")).prepare)) {
          status should equal(400)
          assert(body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }

    "return invalid csv rows" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val tenant = addTenantToDB()
        post(
          "/pocs/create",
          body = badCsv.getBytes(),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          assert(body == CsvConstants.headerErrorMsg("poc_id*", CsvConstants.externalId))
        }
      }
    }
  }

  "Endpoint GET pocStatus" must {
    "return valid pocStatus" in {
      withInjector { Injector =>
        val repo = Injector.get[PocStatusRepository]
        val token = Injector.get[FakeTokenCreator]
        val pocStatus = createPocStatus()
        val res1 = for {
          _ <- repo.createPocStatus(pocStatus)
          data <- repo.getPocStatus(pocStatus.pocId)
        } yield {
          data
        }
        val storedStatus = await(res1, 5.seconds).get
        storedStatus shouldBe pocStatus.copy(lastUpdated = storedStatus.lastUpdated)
        get(
          s"/pocStatus/${pocStatus.pocId}",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName("tenant")).prepare)) {
          status should equal(200)
          assert(body == write[PocStatus](storedStatus))
        }
      }
    }

    "return resource not found error" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val randomID = UUID.randomUUID()
        get(
          s"/pocStatus/$randomID",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName("tenant")).prepare)) {
          status should equal(404)
          assert(body == s"NOK(1.0,false,'ResourceNotFoundError,pocStatus with $randomID couldn't be found)")
        }
      }
    }
  }

  "Endpoint GET /pocs" must {
    "return only pocs of the tenant" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val pocTable = Injector.get[PocRepository]
        val tenant = addTenantToDB()
        val r = for {
          _ <- pocTable.createPoc(createPoc(poc1id, tenant.tenantName))
          _ <- pocTable.createPoc(createPoc(poc2id, tenant.tenantName))
          _ <- pocTable.createPoc(createPoc(
            UUID.randomUUID(),
            TenantName("other tenant")))
          pocs <- pocTable.getAllPocsByTenantId(tenant.id)
        } yield {
          pocs
        }
        val pocs = await(r, 5.seconds).filter(_.tenantId == tenant.id)
        pocs.size shouldBe 2
        get(s"/pocs", headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          val poC_OUT = read[PoC_OUT](body)
          poC_OUT.total shouldBe 2
          poC_OUT.pocs shouldBe pocs.filter(_.tenantId == tenant.id)
        }
      }
    }

    "return Bad Request when tenant doesn't exist" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
//        get(s"/pocs", headers = Map("authorization" -> token.userOnDevicesKeycloak.prepare)) {
        get(s"/pocs", headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName("tenantName")).prepare)) {
          status should equal(400)
          assert(body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }

    "return Success also when list of PoCs is empty" in {
      withInjector { Injector =>
        val tenant = addTenantToDB()
        val token = Injector.get[FakeTokenCreator]
        get(s"/pocs", headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          val poC_OUT = read[PoC_OUT](body)
          poC_OUT.total shouldBe 0
          poC_OUT.pocs should have size 0
        }
      }
    }

    "return Bad Request when user is no tenant admin" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        get(s"/pocs", headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(403)
          assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
        }
      }
    }

    "return PoC page for given index and size" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB()

      val r = for {
        _ <- pocTable.createPoc(createPoc(poc1id, tenant.tenantName))
        _ <- pocTable.createPoc(createPoc(poc2id, tenant.tenantName))
        _ <- pocTable.createPoc(createPoc(UUID.randomUUID(), tenant.tenantName))
        _ <- pocTable.createPoc(createPoc(UUID.randomUUID(), tenant.tenantName))
        _ <- pocTable.createPoc(createPoc(UUID.randomUUID(), tenant.tenantName))
        _ <- pocTable.createPoc(createPoc(UUID.randomUUID(), TenantName("some name")))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds)
      get(
        "/pocs",
        params = Map("pageIndex" -> "1", "pageSize" -> "2"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[PoC_OUT](body)
        poC_OUT.total shouldBe 5
        poC_OUT.pocs shouldBe pocs.slice(2, 4)
      }
    }

    "return PoCs for passed search query" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB()
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, name = "POC 1"))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, name = "POC 11"))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, name = "POC 2"))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds)
      get(
        "/pocs",
        params = Map("search" -> "POC 1"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[PoC_OUT](body)
        poC_OUT.total shouldBe 2
        poC_OUT.pocs shouldBe pocs.filter(_.pocName.startsWith("POC 1"))
      }
    }

    "return PoCs ordered asc by field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB()
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, name = "POC 1"))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, name = "POC 11"))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, name = "POC 2"))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds).sortBy(_.pocName)
      get(
        "/pocs",
        params = Map("sortColumn" -> "pocName", "sortOrder" -> "asc"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[PoC_OUT](body)
        poC_OUT.total shouldBe 3
        poC_OUT.pocs shouldBe pocs
      }
    }

    "return PoCs ordered desc by field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB()
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, name = "POC 1"))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, name = "POC 11"))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, name = "POC 2"))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds).sortBy(_.pocName).reverse
      get(
        "/pocs",
        params = Map("sortColumn" -> "pocName", "sortOrder" -> "desc"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[PoC_OUT](body)
        poC_OUT.total shouldBe 3
        poC_OUT.pocs shouldBe pocs
      }
    }

    "return only pocs with matching status" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB()
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, status = Pending))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, status = Processing))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, status = Completed))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds).filter(p => Seq(Pending, Processing).contains(p.status))
      get(
        "/pocs",
        params = Map("filterColumnStatus" -> "pending,processing"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[PoC_OUT](body)
        poC_OUT.total shouldBe 2
        poC_OUT.pocs shouldBe pocs
      }
    }

    "return pocs with each status when filterColumnStatus parameter is empty" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB()
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, status = Pending))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, status = Processing))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, status = Completed))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds)
      get(
        "/pocs",
        params = Map("filterColumnStatus" -> ""),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[PoC_OUT](body)
        poC_OUT.total shouldBe 3
        poC_OUT.pocs shouldBe pocs
      }
    }
  }

  private val invalidParameterPocs =
    Table(
      ("param", "value"),
      ("filterColumnStatus", "invalid"),
      ("sortColumn", "invalid"),
      ("sortColumn", ""),
      ("sortOrder", "invalid"),
      ("sortOrder", ""),
      ("pageIndex", "invalid"),
      ("pageIndex", "-1"),
      ("pageIndex", ""),
      ("pageSize", "invalid"),
      ("pageSize", "-1"),
      ("pageSize", "")
    )

  forAll(invalidParameterPocs) { (param, value) =>
    s"Endpoint GET /pocs must respond with a bad request when provided an invalid value '$value' for '$param'" in withInjector {
      Injector =>
        val token = Injector.get[FakeTokenCreator]
        val tenant = addTenantToDB()
        get(
          "/pocs",
          params = Map(param -> value, "sortOrder" -> "dsadas"),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
        ) {
          status should equal(400)
          val errorResponse = read[ValidationErrorsResponse](body)
          errorResponse.validationErrors.filter(_.name == param) should have size 1
        }
    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

  override protected def beforeAll: Unit = {
    super.beforeAll()
    withInjector { injector =>
      lazy val pool = injector.get[PublicKeyPoolService]
      await(pool.init(UsersKeycloak, DeviceKeycloak), 2.seconds)

      lazy val tenantAdminController = injector.get[TenantAdminController]
      addServlet(tenantAdminController, "/*")
    }
  }

  private def addTenantToDB(): Tenant = {
    withInjector { injector =>
      val tenantTable = injector.get[TenantTable]
      val tenant = createTenant()
      await(tenantTable.createTenant(tenant), 5.seconds)
      tenant
    }
  }

}
