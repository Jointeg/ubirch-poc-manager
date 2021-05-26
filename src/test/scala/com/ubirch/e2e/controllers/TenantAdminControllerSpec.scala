package com.ubirch.e2e.controllers

import cats.implicits._
import com.ubirch.FakeTokenCreator
import com.ubirch.ModelCreationHelper._
import com.ubirch.controllers.TenantAdminController
import com.ubirch.controllers.TenantAdminController.{ Paginated_OUT, PocAdmin_OUT }
import com.ubirch.data.KeycloakTestData
import com.ubirch.db.tables._
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.ValidationErrorsResponse
import com.ubirch.models.poc._
import com.ubirch.models.tenant._
import com.ubirch.models.user.UserId
import com.ubirch.models.tenant.{ Tenant, TenantId, TenantName }
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.formats.{ CustomFormats, JodaDateTimeFormats }
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.util.CsvConstants
import com.ubirch.services.poc.util.CsvConstants.{ columnSeparator, pocHeaderLine }
import com.ubirch.services.poc.{ PocAdminCreator, PocCreator }
import com.ubirch.services.superadmin.SuperAdminService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import io.prometheus.client.CollectorRegistry
import org.joda.time.{ DateTime, DateTimeZone }
import org.json4s._
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.{ read, write }
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatra.{ BadRequest, Conflict, Ok }

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt

class TenantAdminControllerSpec
  extends E2ETestBase
  with TableDrivenPropertyChecks
  with BeforeAndAfterEach
  with BeforeAndAfterAll {

  import TenantAdminControllerSpec._

  private val poc1id: UUID = UUID.randomUUID()
  private val poc2id: UUID = UUID.randomUUID()
  implicit private val formats: Formats =
    DefaultFormats.lossless ++ CustomFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all ++ JodaDateTimeFormats.all

  private val badCsv =
    "poc_id*;poc_type*;poc_name*;poc_street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;client_cert;data_schema_id*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12A636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12A636;Wunschstadt;;;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}"

  private val goodCsv =
    s"""$pocHeaderLine
       |${poc1id.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val addDeviceCreationToken: String =
    s"""
       |{
       |    "token" : "1234567890"
       |}
       |""".stripMargin

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

    "return invalid csv row in case of db errors" in {
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

        post(
          "/pocs/create",
          body = goodCsv.getBytes(),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          assert(body == goodCsv + columnSeparator + "error on persisting objects; the pair of (external_id and data_schema_id) already exists.")
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
        val pocs = await(r, 5.seconds).filter(_.tenantId == tenant.id).map(_.datesToIsoFormat)
        pocs.size shouldBe 2
        get(s"/pocs", headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          val poC_OUT = read[Paginated_OUT[Poc]](body)
          poC_OUT.total shouldBe 2
          poC_OUT.records shouldBe pocs.filter(_.tenantId == tenant.id)
        }
      }
    }

    "return Bad Request when tenant doesn't exist" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
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
          val poC_OUT = read[Paginated_OUT[Poc]](body)
          poC_OUT.total shouldBe 0
          poC_OUT.records should have size 0
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
      val pocs = await(r, 5.seconds).map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("pageIndex" -> "1", "pageSize" -> "2"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 5
        poC_OUT.records shouldBe pocs.slice(2, 4)
      }
    }

    "return PoCs for passed search by pocName" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB()
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, name = "POC 1"))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, name = "POC 11"))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, name = "POC 2"))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds).map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("search" -> "POC 1"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 2
        poC_OUT.records shouldBe pocs.filter(_.pocName.startsWith("POC 1"))
      }
    }

    "return PoCs for passed search by city" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB()
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, city = "Berlin 1"))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, city = "Berlin 11"))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, city = "Berlin 2"))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds).map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("search" -> "Berlin 1"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 2
        poC_OUT.records shouldBe pocs.filter(_.address.city.startsWith("Berlin 1"))
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
      val pocs = await(r, 5.seconds).sortBy(_.pocName).map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("sortColumn" -> "pocName", "sortOrder" -> "asc"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 3
        poC_OUT.records shouldBe pocs
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
      val pocs = await(r, 5.seconds).sortBy(_.pocName).reverse.map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("sortColumn" -> "pocName", "sortOrder" -> "desc"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 3
        poC_OUT.records shouldBe pocs
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
      val pocs = await(r, 5.seconds).filter(p => Seq(Pending, Processing).contains(p.status)).map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("filterColumn[status]" -> "pending,processing"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 2
        poC_OUT.records shouldBe pocs
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
      val pocs = await(r, 5.seconds).map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("filterColumn[status]" -> ""),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 3
        poC_OUT.records shouldBe pocs
      }
    }
  }

  "Endpoint POST /tenant-token" must {

    "be able to update device creation token by a tenant admin" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenantTable = injector.get[TenantRepository]
        val aesEncryption = injector.get[AESEncryption]
        val tenant = addTenantToDB()

        post(
          "/deviceToken",
          body = addDeviceCreationToken.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
        ) {
          status should equal(200)
          assert(body == "")
        }

        val updatedTenant = tenantTable.getTenant(tenant.id).runSyncUnsafe()
        val decryptedDeviceCreationToken =
          await(aesEncryption.decrypt(updatedTenant.value.deviceCreationToken.get.value)(_.value), 1.second)
        decryptedDeviceCreationToken shouldBe "1234567890"
      }
    }

    "return unauthorized" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        post(
          "/deviceToken",
          body = addDeviceCreationToken.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.superAdmin.prepare)
        ) {
          status should equal(403)
          assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
        }
      }
    }

    "return Bad Request when tenant doesn't exist" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        post(
          "/deviceToken",
          body = addDeviceCreationToken.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName("tenantName")).prepare)
        ) {
          status should equal(400)
          assert(body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }
  }

  "Endpoint GET /poc-admins" must {

    "return only poc admins of the tenant" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val repository = Injector.get[PocAdminRepository]
        val tenant = addTenantToDB()
        val poc = addPocToDb(tenant, Injector.get[PocTable])

        val r = for {
          _ <- repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            webIdentInitiateId = Some(UUID.randomUUID())))
          _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id))
          _ <- repository.createPocAdmin(createPocAdmin(tenantId = TenantId(TenantName("other")), pocId = poc.id))
          admins <- repository.getAllPocAdminsByTenantId(tenant.id)
        } yield admins

        val admins = await(r, 5.seconds).filter(_.tenantId == tenant.id).map(_.datesToIsoFormat)
        admins.size shouldBe 2
        get(s"/poc-admins", headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          val out = read[Paginated_OUT[PocAdmin_OUT]](body)
          out.total shouldBe 2
          out.records shouldBe admins.filter(_.tenantId == tenant.id).map(_.toPocAdminOut(poc))
        }
      }
    }

    "return Bad Request when tenant doesn't exist" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        get(
          s"/poc-admins",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName("tenantName")).prepare)) {
          status should equal(400)
          assert(body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }

    "return Success also when list of PoC admins is empty" in {
      withInjector { Injector =>
        val tenant = addTenantToDB()
        val token = Injector.get[FakeTokenCreator]
        get(s"/poc-admins", headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          val out = read[Paginated_OUT[PocAdmin_OUT]](body)
          out.total shouldBe 0
          out.records should have size 0
        }
      }
    }

    "return Bad Request when user is no tenant admin" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        get(s"/poc-admins", headers = Map("authorization" -> token.superAdmin.prepare)) {
          status should equal(403)
          assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
        }
      }
    }

    "return PoC admins page for given index and size" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, Injector.get[PocTable])

      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = TenantId(TenantName("other")), pocId = poc.id))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).map(_.toPocAdminOut(poc))
      get(
        "/poc-admins",
        params = Map("pageIndex" -> "1", "pageSize" -> "2"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val out = read[Paginated_OUT[PocAdmin_OUT]](body)
        out.total shouldBe 5
        out.records shouldBe pocAdmins.slice(2, 4)
      }
    }

    "return PoC admins for passed search by email" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <-
          repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, email = "admin1@example.com"))
        _ <-
          repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, email = "admin11@example.com"))
        _ <-
          repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, email = "admi212@example.com"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).map(_.toPocAdminOut(poc))
      get(
        "/poc-admins",
        params = Map("search" -> "admin1"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val out = read[Paginated_OUT[PocAdmin_OUT]](body)
        out.total shouldBe 2
        out.records shouldBe pocAdmins.filter(_.email.startsWith("admin1"))
      }
    }

    "return PoC admins for passed search by name" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <-
          repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            email = "admin1@example.com",
            name = "PocAdmin 1"))
        _ <-
          repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            email = "admin11@example.com",
            name = "PocAdmin 11"))
        _ <-
          repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            email = "admin2@example.com",
            name = "PocAdmin 2"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).map(_.toPocAdminOut(poc))
      get(
        "/poc-admins",
        params = Map("search" -> "PocAdmin 1"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val out = read[Paginated_OUT[PocAdmin_OUT]](body)
        out.total shouldBe 2
        out.records shouldBe pocAdmins.filter(_.firstName.startsWith("PocAdmin 1"))
      }
    }

    "return PoC admins ordered asc by field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin1"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin2"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin3"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).sortBy(_.name).map(_.toPocAdminOut(poc))
      get(
        "/poc-admins",
        params = Map("sortColumn" -> "name", "sortOrder" -> "asc"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val out = read[Paginated_OUT[PocAdmin_OUT]](body)
        out.total shouldBe 3
        out.records shouldBe pocAdmins
      }
    }

    "return PoC admins ordered desc by field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin1"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin2"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin3"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).sortBy(_.name).reverse.map(_.toPocAdminOut(poc))
      get(
        "/poc-admins",
        params = Map("sortColumn" -> "name", "sortOrder" -> "desc"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val out = read[Paginated_OUT[PocAdmin_OUT]](body)
        out.total shouldBe 3
        out.records shouldBe pocAdmins
      }
    }

    "return PoC admins ordered by pocName field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val pocTable = Injector.get[PocTable]
      val r = for {
        pocIdB <- pocTable.createPoc(createPoc(name = "POC B", tenantName = tenant.tenantName))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = pocIdB))
        pocIdA <- pocTable.createPoc(createPoc(name = "POC A", tenantName = tenant.tenantName))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = pocIdA))
        pocIdC <- pocTable.createPoc(createPoc(name = "POC C", tenantName = tenant.tenantName))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = pocIdC))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      await(r, 5.seconds)

      get(
        "/poc-admins",
        params = Map("sortColumn" -> "pocName", "sortOrder" -> "asc"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val out = read[Paginated_OUT[PocAdmin_OUT]](body)
        out.total shouldBe 3
        out.records.map(_.pocName) shouldBe Seq("POC A", "POC B", "POC C")
      }
    }

    "return only poc admins with matching status" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Pending))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Processing))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Completed))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins =
        await(r, 5.seconds).filter(p => Seq(Pending, Processing).contains(p.status)).map(_.toPocAdminOut(poc))
      get(
        "/poc-admins",
        params = Map("filterColumn[status]" -> "pending,processing"),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val out = read[Paginated_OUT[PocAdmin_OUT]](body)
        out.total shouldBe 2
        out.records shouldBe pocAdmins
      }
    }

    "return poc admins with each status when filterColumnStatus parameter is empty" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Pending))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Processing))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Completed))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).map(_.toPocAdminOut(poc))
      get(
        "/poc-admins",
        params = Map("filterColumn[status]" -> ""),
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        val out = read[Paginated_OUT[PocAdmin_OUT]](body)
        out.total shouldBe 3
        out.records shouldBe pocAdmins
      }
    }
  }

  private val invalidParameter =
    Table(
      ("param", "value"),
      ("filterColumn[status]", "invalid"),
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

  forAll(invalidParameter) { (param, value) =>
    s"Endpoint GET /pocs must respond with a bad request when provided an invalid value '$value' for '$param'" in withInjector {
      Injector =>
        val token = Injector.get[FakeTokenCreator]
        val tenant = addTenantToDB()
        get(
          "/pocs",
          params = Map(param -> value),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
        ) {
          status should equal(400)
          val errorResponse = read[ValidationErrorsResponse](body)
          errorResponse.validationErrors should have size 1
          errorResponse.validationErrors.filter(_.name == param) should have size 1
        }
    }
  }

  forAll(invalidParameter) { (param, value) =>
    s"Endpoint GET /poc-admins must respond with a bad request when provided an invalid value '$value' for '$param'" in withInjector {
      Injector =>
        val token = Injector.get[FakeTokenCreator]
        val tenant = addTenantToDB()
        get(
          "/poc-admins",
          params = Map(param -> value),
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
        ) {
          status should equal(400)
          val errorResponse = read[ValidationErrorsResponse](body)
          errorResponse.validationErrors should have size 1
          errorResponse.validationErrors.filter(_.name == param) should have size 1
        }
    }
  }

  "Endpoint GET /devices" should {
    "return only simplified device info for PoCs belonging to tenant" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val pocTable = Injector.get[PocRepository]
        val tenant = addTenantToDB()
        val poc1 = createPoc(poc1id, tenant.tenantName)
        val poc2 = createPoc(poc2id, tenant.tenantName)
        val r = for {
          _ <- pocTable.createPoc(poc1)
          _ <- pocTable.createPoc(poc2)
          _ <- pocTable.createPoc(createPoc(
            UUID.randomUUID(),
            TenantName("other tenant")))
        } yield ()
        await(r, 5.seconds)

        get(s"/devices", headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          header("Content-Disposition") shouldBe "attachment; filename=simplified-devices-info.csv"
          header("Content-Type") shouldBe "text/csv;charset=utf-8"
          val bodyLines = body.split("\n")
          bodyLines.size shouldBe 3
          bodyLines should contain(""""externalId"; "pocName"; "deviceId"""")
          bodyLines should contain(s""""${poc1.externalId}"; "${poc1.pocName}"; "${poc1.deviceId.toString}"""")
          bodyLines should contain(s""""${poc2.externalId}"; "${poc2.pocName}"; "${poc2.deviceId.toString}"""")
        }
      }
    }

    "return empty list of simplified device info if there are no PoCs for given tenant" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val pocTable = Injector.get[PocRepository]
        val tenant = addTenantToDB()
        val r = for {
          _ <- pocTable.createPoc(createPoc(
            UUID.randomUUID(),
            TenantName("other tenant")))
        } yield ()
        await(r, 5.seconds)

        get(s"/devices", headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          header("Content-Disposition") shouldBe "attachment; filename=simplified-devices-info.csv"
          header("Content-Type") shouldBe "text/csv;charset=utf-8"
          val bodyLines = body.split("\n")
          bodyLines.size shouldBe 1
          bodyLines should contain(""""externalId"; "pocName"; "deviceId"""")
        }
      }
    }
  }

  "Endpoint POST /webident/initiate-id" should {

    def initiateIdJson(pocAdminId: UUID) =
      s"""
         |{
         |  "pocAdminId": "${pocAdminId.toString}"
         |}
         |""".stripMargin

    "create WebInitiateId on first time and return conflict on second time" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val tenant = addTenantToDB()
        val poc = createPoc(poc1id, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
        } yield ()
        await(r, 5.seconds)

        post(
          "/webident/initiate-id",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare),
          body = initiateIdJson(pocAdmin.id).getBytes(StandardCharsets.UTF_8)
        ) {
          status shouldBe Ok().status
          val updatedPocAdmin = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds)
          body shouldBe
            s"""{"webInitiateId":"${updatedPocAdmin.value.webIdentInitiateId.value.toString}"}""".stripMargin
        }

        val firstWebIdentInitiatedId =
          await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds).value.webIdentInitiateId.value

        post(
          "/webident/initiate-id",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare),
          body = initiateIdJson(pocAdmin.id).getBytes(StandardCharsets.UTF_8)
        ) {
          status shouldBe Conflict().status
          body shouldBe
            s"""{"webInitiateId":"${firstWebIdentInitiatedId.toString}"}""".stripMargin
        }

        val secondWebIdentInitiatedId =
          await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds).value.webIdentInitiateId.value

        firstWebIdentInitiatedId shouldBe secondWebIdentInitiatedId
      }
    }
  }

  "Endpoint POST /webident/id" should {
    def initiateIdJson(pocAdminId: UUID) =
      s"""
         |{
         |  "pocAdminId": "${pocAdminId.toString}"
         |}
         |""".stripMargin

    def updateWebIdentIdJson(pocAdminId: UUID, webIdentId: UUID, webIdentInitiateId: UUID) =
      s"""
         |{
         |  "pocAdminId": "${pocAdminId.toString}",
         |  "webIdentId": "${webIdentId.toString}",
         |  "webIdentInitiateId": "${webIdentInitiateId.toString}"
         |}
         |""".stripMargin
    "Be able to update WebIdentId once the WebInitiateId is set up" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant1 = createTenant("tenant1")
        val tenant2 = createTenant("tenant2")
        val poc = createPoc(poc1id, tenant1.tenantName)
        val pocAdmin1 = createPocAdmin(pocId = poc.id, tenantId = tenant1.id)
        val pocAdmin2 = createPocAdmin(pocId = poc.id, tenantId = tenant2.id)
        val pocAdminStatus1 = createPocAdminStatus(pocAdmin1, poc)
        val pocAdminStatus2 = createPocAdminStatus(pocAdmin2, poc)
        val r = for {
          _ <- tenantTable.createTenant(tenant1)
          _ <- tenantTable.createTenant(tenant2)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin1)
          _ <- pocAdminTable.createPocAdmin(pocAdmin2)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus1)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus2)
        } yield ()
        await(r, 5.seconds)

        post(
          "/webident/id",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant1.tenantName).prepare),
          body = updateWebIdentIdJson(pocAdmin1.id, UUID.randomUUID(), UUID.randomUUID()).getBytes
        ) {
          status shouldBe BadRequest().status
          body shouldBe "Wrong WebIdentInitialId"
        }

        post(
          "/webident/initiate-id",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant1.tenantName).prepare),
          body = initiateIdJson(pocAdmin1.id).getBytes(StandardCharsets.UTF_8)
        ) {
          status shouldBe Ok().status
        }

        val updatedPocAdmin1 = await(pocAdminTable.getPocAdmin(pocAdmin1.id), 5.seconds)

        val webIdentId = UUID.randomUUID()
        post(
          "/webident/id",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant1.tenantName).prepare),
          body = updateWebIdentIdJson(
            pocAdmin1.id,
            webIdentId,
            updatedPocAdmin1.value.webIdentInitiateId.value).getBytes
        ) {
          status shouldBe Ok().status
          body shouldBe ""
        }

        val pocAdmin2AfterOperations = await(pocAdminTable.getPocAdmin(pocAdmin2.id), 5.seconds)
        pocAdmin2AfterOperations.value shouldBe pocAdmin2.copy(lastUpdated = pocAdmin2AfterOperations.value.lastUpdated)
        val pocAdminStatus2AfterOperations = await(pocAdminStatusTable.getStatus(pocAdmin2.id), 5.seconds)
        pocAdminStatus2AfterOperations.value shouldBe pocAdminStatus2.copy(lastUpdated =
          pocAdminStatus2AfterOperations.value.lastUpdated)
      }
    }
  }

  "Endpoint GET /poc-admin/status/:id" should {
    "return status for asked PocAdmin" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val poc = createPoc(poc1id, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc).copy(
          webIdentRequired = true,
          webIdentInitiated = Some(true),
          webIdentSuccess = Some(false),
          invitedToTeamDrive = None,
          errorMessage = Some("random error message")
        )
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val pocAdminStatusAfterInsert = await(pocAdminStatusTable.getStatus(pocAdmin.id), 5.seconds).getOrElse(fail(
          s"Expected to have PoC Admin status with id ${pocAdmin.id}"))

        get(
          s"/poc-admin/status/${pocAdmin.id}",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(200)
          pretty(render(parse(body))) shouldBe
            s"""|{
                |  "webIdentRequired" : true,
                |  "webIdentInitiated" : true,
                |  "webIdentSuccess" : false,
                |  "certifyUserCreated" : false,
                |  "pocAdminGroupAssigned" : false,
                |  "keycloakEmailSent" : false,
                |  "errorMessage" : "random error message",
                |  "lastUpdated" : "${pocAdminStatusAfterInsert.lastUpdated.dateTime.withZone(DateTimeZone.UTC).toString()}",
                |  "created" : "${pocAdminStatusAfterInsert.created.dateTime.withZone(DateTimeZone.UTC).toString()}"
                |}""".stripMargin
        }
      }
    }

    "fail with 500 if the provided PocAdmin ID can't be converted to UUID" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val tenantTable = injector.get[TenantRepository]
        val tenant = createTenant()
        val poc = createPoc(poc1id, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val pocAdminStatus = createPocAdminStatus(pocAdmin, poc)
        val r = for {
          _ <- tenantTable.createTenant(tenant)
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
          _ <- pocAdminStatusTable.createStatus(pocAdminStatus)
        } yield ()
        await(r, 5.seconds)

        val pocAdminStatusAfterInsert = await(pocAdminStatusTable.getStatus(pocAdmin.id), 5.seconds).getOrElse(fail(
          s"Expected to have PoC Admin status with id ${pocAdmin.id}"))

        get(
          s"/poc-admin/status/wrongUUID",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)) {
          status should equal(400)
          assert(body.contains("Invalid UUID string"))
        }
      }
    }
  }

  "Endpoint PUT /poc-admin/:id/active/:isActive" should {
    "deactivate, and activate user" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val keycloakUserService = i.get[KeycloakUserService]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, i.get[PocTable])
      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        KeycloakTestData.createNewCertifyKeycloakUser(),
        CertifyKeycloak))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id, certifyUserId = Some(certifyUserId.value))
      val id = await(repository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id/active/0",
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        body shouldBe empty
        await(repository.getPocAdmin(id)).value.active shouldBe false
        await(
          keycloakUserService.getUserById(UserId(certifyUserId.value), CertifyKeycloak)).value.isEnabled shouldBe false
      }

      put(
        s"/poc-admin/$id/active/1",
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(200)
        body shouldBe empty
        await(repository.getPocAdmin(id)).value.active shouldBe true
        await(
          keycloakUserService.getUserById(UserId(certifyUserId.value), CertifyKeycloak)).value.isEnabled shouldBe true
      }
    }

    "return 404 when poc-admin does not exist" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val tenant = addTenantToDB()
      val invalidPocAdminId = UUID.randomUUID()

      put(
        s"/poc-admin/$invalidPocAdminId/active/0",
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc admin with id '$invalidPocAdminId' not found"))
      }
    }

    "return 400 when isActive is invalid value" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val tenant = addTenantToDB()
      val invalidPocAdminId = UUID.randomUUID()

      put(
        s"/poc-admin/$invalidPocAdminId/active/2",
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(400)
        assert(body.contains("Illegal value for ActivateSwitch: 2. Expected 0 or 1"))
      }
    }

    "return 409 when poc-admin does not have certifyUserId" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val tenant = addTenantToDB()
      val poc = addPocToDb(tenant, i.get[PocTable])
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id, certifyUserId = None)
      val id = await(repository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id/active/1",
        headers = Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc admin '$id' does not have certifyUserId"))
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
      await(pool.init(CertifyKeycloak, DeviceKeycloak), 2.seconds)

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

  private def addPocToDb(tenant: Tenant, pocTable: PocTable): Poc = {
    val poc = createPoc(tenantName = tenant.tenantName, status = Pending)
    await(pocTable.createPoc(poc), 5.seconds)
    poc
  }

}

object TenantAdminControllerSpec {
  implicit class PocOps(poc: Poc) {
    def datesToIsoFormat: Poc = {
      poc.copy(
        created = Created(DateTime.parse(Instant.ofEpochMilli(poc.created.dateTime.getMillis).toString)),
        lastUpdated = Updated(DateTime.parse(Instant.ofEpochMilli(poc.lastUpdated.dateTime.getMillis).toString))
      )
    }
  }

  implicit class PocAdminOps(pa: PocAdmin) {
    def datesToIsoFormat: PocAdmin = {
      pa.copy(
        created = Created(DateTime.parse(Instant.ofEpochMilli(pa.created.dateTime.getMillis).toString)),
        lastUpdated = Updated(DateTime.parse(Instant.ofEpochMilli(pa.lastUpdated.dateTime.getMillis).toString))
      )
    }

    def toPocAdminOut(poc: Poc): PocAdmin_OUT = PocAdmin_OUT.fromPocAdmin(pa, poc)
  }
}
