package com.ubirch.e2e.controllers

import cats.implicits._
import com.ubirch.ModelCreationHelper._
import com.ubirch.controllers.TenantAdminController
import com.ubirch.controllers.model.TenantAdminControllerJsonModel.PocAdmin_OUT
import com.ubirch.data.KeycloakTestData
import com.ubirch.db.tables._
import com.ubirch.e2e.E2ETestBase
import com.ubirch.e2e.controllers.assertions.PocAdminJsonAssertion._
import com.ubirch.e2e.controllers.assertions.PocAdminsJsonAssertion._
import com.ubirch.models.keycloak.user.UserRequiredAction
import com.ubirch.models.poc._
import com.ubirch.models.tenant.{ CreatePocAdminRequest, SharedAuthCert, Tenant, TenantId, TenantName }
import com.ubirch.models.user.UserId
import com.ubirch.models.{ FieldError, NOK, Paginated_OUT, ValidationErrorsResponse }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.auth.AESEncryption
import com.ubirch.services.formats.CustomFormats
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.util.CsvConstants
import com.ubirch.services.poc.util.CsvConstants.{ columnSeparator, firstName }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.test.TestData
import com.ubirch.testutils.CentralCsvProvider.{ invalidHeaderPocOnlyCsv, validPocOnlyCsv }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import com.ubirch.{ FakeTokenCreator, FakeX509Certs, InjectorHelper, ModelCreationHelper }
import io.prometheus.client.CollectorRegistry
import monix.eval.Task
import org.joda.time
import org.joda.time.{ DateTime, DateTimeZone, LocalDate }
import org.json4s._
import org.json4s.ext.{ JavaTypesSerializers, JodaTimeSerializers }
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization.{ read, write }
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{ AppendedClues, BeforeAndAfterAll, BeforeAndAfterEach }
import org.scalatra.{ BadRequest, Conflict, Ok }

import java.nio.charset.StandardCharsets
import java.time.{ Clock, Instant }
import java.util.UUID
import scala.concurrent.duration.DurationInt

class TenantAdminControllerSpec
  extends E2ETestBase
  with TableDrivenPropertyChecks
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with X509CertTests
  with AppendedClues {

  import TenantAdminControllerSpec._

  private val poc1id: UUID = UUID.randomUUID()
  private val poc2id: UUID = UUID.randomUUID()
  private val pocAdminId: UUID = UUID.randomUUID()
  implicit private val formats: Formats =
    DefaultFormats.lossless ++ new CustomFormats().formats ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all

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
        val tenant = addTenantToDB(Injector)
        post(
          "/pocs/create",
          body = validPocOnlyCsv(poc1id).getBytes(),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
        ) {
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
        addTenantToDB(Injector)
        post(
          "/pocs/create",
          body = validPocOnlyCsv(poc1id).getBytes(),
          headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)) {
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
          body = validPocOnlyCsv(poc1id).getBytes(),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName("tenantName")).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(400)
          assert(body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }

    "return invalid csv rows" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val tenant = addTenantToDB(Injector)
        post(
          "/pocs/create",
          body = invalidHeaderPocOnlyCsv.getBytes(),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(200)
          assert(body == CsvConstants.headerErrorMsg("poc_id*", CsvConstants.externalId))
        }
      }
    }

    "return invalid csv row in case of db errors" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val tenant = addTenantToDB(Injector)
        post(
          "/pocs/create",
          body = validPocOnlyCsv(poc1id).getBytes(),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(200) withClue s"Error response: $body"
          assert(body.isEmpty)
        }

        post(
          "/pocs/create",
          body = validPocOnlyCsv(poc1id).getBytes(),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(200) withClue s"Error response: $body"
          assert(body == validPocOnlyCsv(
            poc1id) + columnSeparator + "error on persisting objects; the pair of (external_id and data_schema_id) already exists.")
        }
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = POST,
      path = "/pocs/create",
      requestBody = validPocOnlyCsv(poc1id),
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[TenantName](
      method = POST,
      path = "/pocs/create",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      responseAssertion = (body, _) => assert(body == ""),
      payload = TenantName(getRandomString),
      before = (injector, _) => addTenantToDB(injector),
      requestBody = _ => validPocOnlyCsv(poc1id)
    )
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
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName("tenant")).prepare,
            FakeX509Certs.validX509Header)
        ) {
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
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName("tenant")).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(404)
          assert(body == s"NOK(1.0,false,'ResourceNotFoundError,pocStatus with $randomID couldn't be found)")
        }
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = s"/pocStatus/$poc1id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector))

    x509SuccessWhenNonBlockingIssuesWithCert[PocStatus](
      method = GET,
      path = s"/pocStatus/$poc1id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      responseAssertion = (body, pocStatus) => assert(body.contains(s""""pocId":"${pocStatus.pocId}"""")),
      payload = createPocStatus(pocId = poc1id),
      before = (injector, pocStatus) => {
        val repo = injector.get[PocStatusRepository]
        await(repo.createPocStatus(pocStatus))
        addTenantToDB(injector)
      }
    )
  }

  "Endpoint GET /pocs" must {
    "return only pocs of the tenant" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val pocTable = Injector.get[PocRepository]
        val tenant = addTenantToDB(Injector)
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
        get(
          s"/pocs",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)) {
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
        get(
          s"/pocs",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName("tenantName")).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(400)
          assert(body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }

    "return Success also when list of PoCs is empty" in {
      withInjector { Injector =>
        val tenant = addTenantToDB(Injector)
        val token = Injector.get[FakeTokenCreator]
        get(
          s"/pocs",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)) {
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
      val tenant = addTenantToDB(Injector)

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
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
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
      val tenant = addTenantToDB(Injector)
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, name = "poc 1"))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, name = "the POC 11"))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, name = "POC 2"))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds).map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("search" -> "PoC 1"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 2
        poC_OUT.records shouldBe pocs.filter(_.pocName.toLowerCase.contains("poc 1"))
      }
    }

    "return PoCs for passed search by city" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val r = for {
        _ <- pocTable.createPoc(createPoc(id = poc1id, tenantName = tenant.tenantName, city = "Berlin 1"))
        _ <- pocTable.createPoc(createPoc(id = poc2id, tenantName = tenant.tenantName, city = "the berlin 11"))
        _ <- pocTable.createPoc(createPoc(id = UUID.randomUUID(), tenantName = tenant.tenantName, city = "Berlin 2"))
        pocs <- pocTable.getAllPocsByTenantId(tenant.id)
      } yield pocs
      val pocs = await(r, 5.seconds).map(_.datesToIsoFormat)
      get(
        "/pocs",
        params = Map("search" -> "BerLin 1"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 2
        poC_OUT.records shouldBe pocs.filter(_.address.city.toLowerCase.contains("berlin 1"))
      }
    }

    "return PoCs ordered asc by field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
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
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
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
      val tenant = addTenantToDB(Injector)
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
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
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
      val tenant = addTenantToDB(Injector)
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
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
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
      val tenant = addTenantToDB(Injector)
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
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        val poC_OUT = read[Paginated_OUT[Poc]](body)
        poC_OUT.total shouldBe 3
        poC_OUT.records shouldBe pocs
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = "/pocs",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[Poc](
      method = GET,
      path = s"/pocs",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      responseAssertion = (body, poc) => assert(body.contains(s""""id":"${poc.id}"""")),
      payload = createPoc(id = poc1id, tenantName = TenantName(globalTenantName)),
      before = (injector, poc) => {
        addTenantToDB(injector)
        await(injector.get[PocRepository].createPoc(poc))
      }
    )
  }

  "Endpoint GET /poc/:id" must {
    "return poc for given id" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val poc = createPoc(poc1id, tenant.tenantName)
      val _ = await(pocTable.createPoc(poc))
      val pocFromTable = await(pocTable.getPoc(poc.id)).value

      get(
        s"/poc/$poc1id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(200)
        pretty(render(parse(body))) shouldBe pocToFormattedJson(pocFromTable)
      }
    }

    "return 403 when requesting user is not a tenant-admin" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]

      get(s"/poc/$poc1id", headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)) {
        status should equal(403)
        assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
      }
    }

    "return 404 when poc does not exists" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val tenant = addTenantToDB(Injector)

      get(
        s"/poc/$poc1id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(404)
        assert(body.contains(s"PoC with id '$poc1id' does not exist"))
      }
    }

    "return 401 when poc is not owned by tenant-admin" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)

      val otherTenant = addTenantToDB("otherTenantName", Injector)
      val poc = createPoc(poc1id, otherTenant.tenantName)
      val _ = await(pocTable.createPoc(poc))

      get(
        s"/poc/$poc1id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(401)
        assert(body.contains(s"PoC with id '$poc1id' does not belong to tenant with id '${tenant.id.value.value}'"))
      }
    }

    "return 400 when tenant-admin does not exists" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]

      get(
        s"/poc/$poc1id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(400)
        assert(
          body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = s"/poc/$poc1id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[Poc](
      method = GET,
      path = s"/poc/$poc1id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      responseAssertion = (body, poc) => assert(body.contains(s""""id":"${poc.id}"""")),
      payload = createPoc(id = poc1id, tenantName = TenantName(globalTenantName)),
      before = (injector, poc) => {
        addTenantToDB(injector)
        await(injector.get[PocRepository].createPoc(poc))
      }
    )
  }

  "Endpoint PUT /poc/:id" must {
    "update poc for given id" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val poc = createPoc(poc1id, tenant.tenantName).copy(status = Completed)
      val _ = await(pocTable.createPoc(poc))
      val updatedPoc = await(pocTable.getPoc(poc.id)).value.copy(
        phone = "+4974339346",
        address = Address(
          "new street",
          "1234",
          Some("new additional"),
          21436,
          "new Berlin",
          Some("new county"),
          Some("new federal state"),
          "new Germany"),
        manager = PocManager("new last name", "new name", "new@email.com", "+4974339296")
      )

      put(
        uri = s"/poc/$poc1id",
        body = pocToFormattedJson(updatedPoc).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        body shouldBe "{}"
        status should equal(200)
      }

      val updatedPocFromTable = await(pocTable.getPoc(poc.id)).value

      get(
        s"/poc/$poc1id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(200)
        pretty(render(parse(body))) shouldBe pocToFormattedJson(updatedPoc.copy(lastUpdated =
          updatedPocFromTable.lastUpdated))
      }
    }

    "return badRequest if update PoC data contains invalid email and phone numbers in wrong format" in withInjector {
      injector =>
        val token = injector.get[FakeTokenCreator]
        val pocTable = injector.get[PocRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(poc1id, tenant.tenantName).copy(status = Completed)
        val _ = await(pocTable.createPoc(poc))
        val updatedPoc = await(pocTable.getPoc(poc.id)).value.copy(
          phone = "wrongPhone1",
          address = Address(
            "new street",
            "1234",
            Some("new additional"),
            21436,
            "new Berlin",
            Some("new county"),
            Some("new federal state"),
            "new Germany"),
          manager = PocManager("new last name", "new name", "notanemail", "+980")
        )

        put(
          uri = s"/poc/$poc1id",
          body = pocToFormattedJson(updatedPoc).getBytes,
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(400)
          body shouldBe NOK.badRequest(
            """Invalid poc manager email
              |Invalid phone number
              |Invalid poc manager phone number""".stripMargin).toString
        }
    }

    "return 409 when PoC is not in Completed status" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val poc = createPoc(poc1id, tenant.tenantName).copy(status = Pending)
      val _ = await(pocTable.createPoc(poc))

      put(
        s"/poc/$poc1id",
        body = pocToFormattedJson(poc).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc '$poc1id' is in wrong status: 'Pending', required: 'Completed'"))
      }
    }

    "return 403 when requesting user is not a tenant-admin" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]

      put(s"/poc/$poc1id", headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)) {
        status should equal(403)
        assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
      }
    }

    "return 404 when poc does not exists" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val poc = createPoc(poc1id, tenant.tenantName)

      put(
        s"/poc/$poc1id",
        body = pocToFormattedJson(poc).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(body.contains(s"PoC with id '$poc1id' does not exist"))
      }
    }

    "return 401 when poc is not owned by tenant-admin" in withInjector { implicit Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)

      val otherTenant = addTenantToDB("otherTenantName", Injector)
      val poc = createPoc(poc1id, otherTenant.tenantName)
      val _ = await(pocTable.createPoc(poc))

      put(
        s"/poc/$poc1id",
        body = pocToFormattedJson(poc).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(401)
        assert(body.contains(s"PoC with id '$poc1id' does not belong to tenant with id '${tenant.id.value.value}'"))
      }
    }

    "return 400 when tenant-admin does not exists" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]

      put(
        s"/poc/$poc1id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(400)
        assert(
          body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = PUT,
      path = s"/poc/$poc1id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[Poc](
      method = PUT,
      path = s"/poc/$poc1id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = createPoc(id = poc1id, tenantName = TenantName(globalTenantName), status = Completed).copy(
        phone = "+4974339296",
        manager = PocManager("new last name", "new name", "new@email.com", "+4974339296")
      ),
      requestBody = poc => pocToFormattedJson(poc),
      before = (injector, poc) => {
        addTenantToDB(injector)
        await(injector.get[PocRepository].createPoc(poc))
      }
    )
  }

  "Endpoint POST /tenant-token" must {

    "be able to update device creation token by a tenant admin" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenantTable = injector.get[TenantRepository]
        val aesEncryption = injector.get[AESEncryption]
        val tenant = addTenantToDB(injector)

        post(
          "/deviceToken",
          body = addDeviceCreationToken.getBytes(StandardCharsets.UTF_8),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
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
          headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)
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
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName("tenantName")).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(400)
          assert(body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = POST,
      path = "/deviceToken",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector),
      requestBody = addDeviceCreationToken
    )

    x509SuccessWhenNonBlockingIssuesWithCert[String](
      method = POST,
      path = "/deviceToken",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = addDeviceCreationToken,
      requestBody = identity,
      before = (injector, poc) => addTenantToDB(injector)
    )
  }

  "Endpoint GET /poc-admins" must {

    "return only poc admins of the tenant" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        val repository = Injector.get[PocAdminRepository]
        val tenant = addTenantToDB(Injector)
        val poc = addPocToDb(tenant, Injector.get[PocTable])

        val pocAdminA = createPocAdmin(tenantId = tenant.id, pocId = poc.id)
        val pocAdminB = createPocAdmin(tenantId = tenant.id, pocId = poc.id)

        val r = for {
          _ <- repository.createPocAdmin(pocAdminA)
          _ <- repository.createPocAdmin(pocAdminB)
          _ <- repository.createPocAdmin(createPocAdmin(tenantId = TenantId(TenantName("other")), pocId = poc.id))
          r <- repository.getAllPocAdminsByTenantId(tenant.id)
        } yield r
        val pocAdmins = await(r).sorted

        get(
          s"/poc-admins",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(200)
          assertPocAdminsJson(body)
            .hasTotal(2)
            .hasAdminCount(2)
            .hasAdmins(pocAdmins.map(pa => (poc, pa)))
        }
      }
    }

    "return Bad Request when tenant doesn't exist" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        get(
          s"/poc-admins",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName("tenantName")).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(400)
          assert(body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }

    "return Success also when list of PoC admins is empty" in {
      withInjector { Injector =>
        val tenant = addTenantToDB(Injector)
        val token = Injector.get[FakeTokenCreator]
        get(
          s"/poc-admins",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(200)
          assertPocAdminsJson(body)
            .hasTotal(0)
            .hasAdminCount(0)
        }
      }
    }

    "return Bad Request when user is no tenant admin" in {
      withInjector { Injector =>
        val token = Injector.get[FakeTokenCreator]
        get(s"/poc-admins", headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)) {
          status should equal(403)
          assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
        }
      }
    }

    "return PoC admins page for given index and size" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = addPocToDb(tenant, Injector.get[PocTable])

      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, surname = "admin a"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, surname = "admin b"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, surname = "admin c"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, surname = "admin d"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, surname = "admin e"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = TenantId(TenantName("other")), pocId = poc.id))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r).sorted
      get(
        "/poc-admins",
        params = Map("pageIndex" -> "1", "pageSize" -> "2"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasTotal(5)
          .hasAdminCount(2)
          .hasAdmins(pocAdmins.slice(2, 4).map(pa => (poc, pa)))
      }
    }

    "return PoC admins for passed search by email" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <-
          repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, email = "admin1@example.com"))
        _ <-
          repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            email = "theaDmin11@example.com"))
        _ <-
          repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, email = "admi212@example.com"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).sorted
      get(
        "/poc-admins",
        params = Map("search" -> "Admin1"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasAdminCount(2)
          .hasAdmins(pocAdmins.filter(_.email.toLowerCase.contains("admin1")).map(pa => (poc, pa)))
      }
    }

    "return PoC admins for passed search by name" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
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
            name = "the PocAdmin 11"))
        _ <-
          repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            email = "admin2@example.com",
            name = "PocAdmin 2"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).sorted
      get(
        "/poc-admins",
        params = Map("search" -> "pocadmin 1"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasAdminCount(2)
          .hasAdmins(pocAdmins.filter(_.name.toLowerCase.contains("pocadmin 1")).map(pa => (poc, pa)))
      }
    }

    "return PoC admins for passed search by surname" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <-
          repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            email = "admin1@example.com",
            surname = "PocAdmin 1"))
        _ <-
          repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            email = "admin11@example.com",
            surname = "the PocAdmin 11"))
        _ <-
          repository.createPocAdmin(createPocAdmin(
            tenantId = tenant.id,
            pocId = poc.id,
            email = "admin2@example.com",
            surname = "PocAdmin 2"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).sorted
      get(
        "/poc-admins",
        params = Map("search" -> "pocadmin 1"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasTotal(2)
          .hasAdminCount(2)
          .hasAdmins(pocAdmins.filter(_.surname.toLowerCase.contains("pocadmin 1")).map(pa => (poc, pa)))
      }
    }

    "return PoC admins ordered asc by field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin1"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin2"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin3"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r).sortBy(_.name)
      get(
        "/poc-admins",
        params = Map("sortColumn" -> "firstName", "sortOrder" -> "asc"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasTotal(3)
          .hasAdminCount(3)
          .hasAdmins(pocAdmins.map(pa => (poc, pa)))
      }
    }

    "return PoC admins ordered desc by field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin1"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin2"))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, name = "admin3"))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r, 5.seconds).sortBy(_.name).reverse
      get(
        "/poc-admins",
        params = Map("sortColumn" -> "firstName", "sortOrder" -> "desc"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasTotal(3)
          .hasAdminCount(3)
          .hasAdmins(pocAdmins.map(pa => (poc, pa)))
      }
    }

    "return PoC admins ordered by pocName field" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val pocTable = Injector.get[PocTable]
      val pocA = createPoc(name = "POC A", tenantName = tenant.tenantName)
      val pocAdminA = createPocAdmin(tenantId = tenant.id, pocId = pocA.id)
      val pocB = createPoc(name = "POC B", tenantName = tenant.tenantName)
      val pocAdminB = createPocAdmin(tenantId = tenant.id, pocId = pocB.id)
      val pocC = createPoc(name = "POC C", tenantName = tenant.tenantName)
      val pocAdminC = createPocAdmin(tenantId = tenant.id, pocId = pocC.id)

      val r = for {
        _ <- pocTable.createPoc(pocB)
        _ <- repository.createPocAdmin(pocAdminB)
        _ <- pocTable.createPoc(pocA)
        _ <- repository.createPocAdmin(pocAdminA)
        _ <- pocTable.createPoc(pocC)
        _ <- repository.createPocAdmin(pocAdminC)
      } yield ()
      await(r)

      get(
        "/poc-admins",
        params = Map("sortColumn" -> "pocName", "sortOrder" -> "asc"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status shouldBe 200
        assertPocAdminsJson(body)
          .hasTotal(3)
          .hasAdminCount(3)
          .hasAdmins(Seq((pocA, pocAdminA), (pocB, pocAdminB), (pocC, pocAdminC)))
      }
    }

    "return PoC admins ordered by created date asc" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val pocTable = Injector.get[PocTable]
      val poc = createPoc(tenantName = tenant.tenantName)
      val pocAdminA = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        created = time.Instant.parse("2020-01-02T10:00:01Z").toDateTime)
      val pocAdminB = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        created = time.Instant.parse("2020-01-01T10:00:01Z").toDateTime)
      val pocAdminC = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        created = time.Instant.parse("2020-01-01T11:00:01Z").toDateTime)

      val r = for {
        _ <- pocTable.createPoc(poc)
        _ <- repository.createPocAdmin(pocAdminA)
        _ <- repository.createPocAdmin(pocAdminB)
        _ <- repository.createPocAdmin(pocAdminC)
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      await(r, 5.seconds)

      get(
        "/poc-admins",
        params = Map("sortColumn" -> "createdAt", "sortOrder" -> "asc"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasTotal(3)
          .hasAdminCount(3)
          .hasAdmins(Seq(pocAdminB, pocAdminC, pocAdminA).map(pa => (poc, pa)))
      }
    }

    "return PoC admins ordered by created date desc" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val pocTable = Injector.get[PocTable]
      val poc = createPoc(tenantName = tenant.tenantName)
      val pocAdminA = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        created = time.Instant.parse("2020-01-02T10:00:01Z").toDateTime)
      val pocAdminB = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        created = time.Instant.parse("2020-01-01T10:00:01Z").toDateTime)
      val pocAdminC = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        created = time.Instant.parse("2020-01-01T11:00:01Z").toDateTime)

      val r = for {
        _ <- pocTable.createPoc(poc)
        _ <- repository.createPocAdmin(pocAdminA)
        _ <- repository.createPocAdmin(pocAdminB)
        _ <- repository.createPocAdmin(pocAdminC)
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      await(r, 5.seconds)

      get(
        "/poc-admins",
        params = Map("sortColumn" -> "createdAt", "sortOrder" -> "desc"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasTotal(3)
          .hasAdminCount(3)
          .hasAdmins(Seq(pocAdminA, pocAdminC, pocAdminB).map(pa => (poc, pa)))
      }
    }

    "return only poc admins with matching status" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Pending))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Processing))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Completed))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins =
        await(r, 5.seconds).filter(p => Seq(Pending, Processing).contains(p.status)).sorted
      get(
        "/poc-admins",
        params = Map("filterColumn[status]" -> "pending,processing"),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasTotal(2)
          .hasAdminCount(2)
          .hasAdmins(pocAdmins.map(pa => (poc, pa)))
      }
    }

    "return poc admins with each status when filterColumnStatus parameter is empty" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val repository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = addPocToDb(tenant, Injector.get[PocTable])
      val r = for {
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Pending))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Processing))
        _ <- repository.createPocAdmin(createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Completed))
        records <- repository.getAllPocAdminsByTenantId(tenant.id)
      } yield records
      val pocAdmins = await(r).sorted
      get(
        "/poc-admins",
        params = Map("filterColumn[status]" -> ""),
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        assertPocAdminsJson(body)
          .hasTotal(3)
          .hasAdminCount(3)
          .hasAdmins(pocAdmins.map(pa => (poc, pa)))
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = "/poc-admins",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[(Poc, PocAdmin)](
      method = GET,
      path = "/poc-admins",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = (
        createPoc(id = poc1id, tenantName = TenantName(globalTenantName)),
        createPocAdmin(pocId = poc1id, tenantId = TenantId(TenantName(globalTenantName)))),
      before = (injector, pocWithPocAdmin) => {
        val (poc, admin) = pocWithPocAdmin
        val tenant = addTenantToDB(injector)
        await(injector.get[PocRepository].createPoc(poc))
        await(injector.get[PocAdminRepository].createPocAdmin(admin.copy(tenantId = tenant.id)))
      },
      responseAssertion = (body, pocWithPocAdmin) => {
        assertPocAdminsJson(body)
          .hasTotal(1)
          .hasAdminCount(1)
          .hasAdmins(Seq(pocWithPocAdmin))
      }
    )
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
        val tenant = addTenantToDB(Injector)
        get(
          "/pocs",
          params = Map(param -> value),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
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
        val tenant = addTenantToDB(Injector)
        get(
          "/poc-admins",
          params = Map(param -> value),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
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
        val tenant = addTenantToDB(Injector)
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

        get(
          s"/devices",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)) {
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
        val tenant = addTenantToDB(Injector)
        val r = for {
          _ <- pocTable.createPoc(createPoc(
            UUID.randomUUID(),
            TenantName("other tenant")))
        } yield ()
        await(r, 5.seconds)

        get(
          s"/devices",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(200)
          header("Content-Disposition") shouldBe "attachment; filename=simplified-devices-info.csv"
          header("Content-Type") shouldBe "text/csv;charset=utf-8"
          val bodyLines = body.split("\n")
          bodyLines.size shouldBe 1
          bodyLines should contain(""""externalId"; "pocName"; "deviceId"""")
        }
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = "/devices",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[Poc](
      method = GET,
      path = "/devices",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = createPoc(id = poc1id, tenantName = TenantName(globalTenantName)),
      before = (injector, poc) => {
        addTenantToDB(injector)
        await(injector.get[PocRepository].createPoc(poc))
      },
      responseAssertion = (body, poc) => {
        val bodyLines = body.split("\n")
        bodyLines should contain(s""""${poc.externalId}"; "${poc.pocName}"; "${poc.deviceId.toString}"""")
      }
    )
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
        val tenant = addTenantToDB(injector)
        val poc = createPoc(poc1id, tenant.tenantName)
        val pocAdmin = createPocAdmin(pocId = poc.id, tenantId = tenant.id)
        val r = for {
          _ <- pocTable.createPoc(poc)
          _ <- pocAdminTable.createPocAdmin(pocAdmin)
        } yield ()
        await(r, 5.seconds)

        post(
          "/webident/initiate-id",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header),
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
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header),
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

    x509ForbiddenWhenHeaderIsInvalid(
      method = POST,
      path = "/webident/initiate-id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector),
      requestBody = initiateIdJson(poc1id)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[(Poc, PocAdmin)](
      method = POST,
      path = "/webident/initiate-id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = (
        createPoc(id = poc1id, tenantName = TenantName(globalTenantName)),
        createPocAdmin(pocAdminId = pocAdminId, pocId = poc1id, tenantId = TenantId(TenantName(globalTenantName)))),
      requestBody = _ => initiateIdJson(pocAdminId),
      before = (injector, pocWithPocAdmin) => {
        val (poc, admin) = pocWithPocAdmin
        addTenantToDB(injector)
        await(injector.get[PocRepository].createPoc(poc))
        await(injector.get[PocAdminRepository].createPocAdmin(admin))
      },
      responseAssertion = (body, poc) => {
        assert(body.contains("""{"webInitiateId":"""))
      },
      assertion = (injector, pocWithPocAdmin) => {
        val (_, pocAdmin) = pocWithPocAdmin
        val updatedPocAdmin = await(injector.get[PocAdminTable].getPocAdmin(pocAdmin.id))
        assert(updatedPocAdmin.value.webIdentInitiateId.isDefined)
      }
    )
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
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant1.tenantName).prepare,
            FakeX509Certs.validX509Header),
          body = updateWebIdentIdJson(pocAdmin1.id, UUID.randomUUID(), UUID.randomUUID()).getBytes
        ) {
          status shouldBe BadRequest().status
          body shouldBe "NOK(1.0,false,'BadRequest,Wrong WebIdentInitialId)"
        }

        post(
          "/webident/initiate-id",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant1.tenantName).prepare,
            FakeX509Certs.validX509Header),
          body = initiateIdJson(pocAdmin1.id).getBytes(StandardCharsets.UTF_8)
        ) {
          status shouldBe Ok().status
        }

        val updatedPocAdmin1 = await(pocAdminTable.getPocAdmin(pocAdmin1.id), 5.seconds)

        val webIdentId = UUID.randomUUID()
        post(
          "/webident/id",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant1.tenantName).prepare,
            FakeX509Certs.validX509Header),
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

    x509ForbiddenWhenHeaderIsInvalid(
      method = POST,
      path = "/webident/id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[(Poc, PocAdmin)](
      method = POST,
      path = "/webident/id",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = (
        createPoc(id = poc1id, tenantName = TenantName(globalTenantName)),
        createPocAdmin(
          pocAdminId = pocAdminId,
          pocId = poc1id,
          tenantId = TenantId(TenantName(globalTenantName)),
          webIdentInitiateId = Some(TestData.PocAdmin.webIdentInitiateId))),
      requestBody = pocWithPocAdmin => {
        val (_, admin) = pocWithPocAdmin
        updateWebIdentIdJson(
          admin.id,
          TestData.PocAdmin.webIdentId,
          TestData.PocAdmin.webIdentInitiateId)
      },
      before = (injector, pocWithPocAdmin) => {
        val (poc, admin) = pocWithPocAdmin
        addTenantToDB(injector)
        await(injector.get[PocRepository].createPoc(poc))
        await(injector.get[PocAdminRepository].createPocAdmin(admin))
        await(injector.get[PocAdminStatusRepository].createStatus(createPocAdminStatus(admin, poc)))
      },
      responseAssertion = (body, _) => assert(body.isEmpty)
    )
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
        await(r)

        val pocAdminStatusAfterInsert = await(pocAdminStatusTable.getStatus(pocAdmin.id)).getOrElse(fail(
          s"Expected to have PoC Admin status with id ${pocAdmin.id}"))

        get(
          s"/poc-admin/status/${pocAdmin.id}",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
        ) {
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
        await(r)

        await(pocAdminStatusTable.getStatus(pocAdmin.id)).getOrElse(fail(
          s"Expected to have PoC Admin status with id ${pocAdmin.id}"))

        get(
          s"/poc-admin/status/wrongUUID",
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(400)
          assert(body.contains("Invalid UUID string"))
        }
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = s"/poc-admin/status/$pocAdminId",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[(Poc, PocAdmin)](
      method = GET,
      path = s"/poc-admin/status/$pocAdminId",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = (
        createPoc(id = poc1id, tenantName = TenantName(globalTenantName)),
        createPocAdmin(
          pocAdminId = pocAdminId,
          pocId = poc1id,
          tenantId = TenantId(TenantName(globalTenantName)),
          webIdentInitiateId = Some(TestData.PocAdmin.webIdentInitiateId))),
      before = (injector, pocWithPocAdmin) => {
        val (poc, admin) = pocWithPocAdmin
        addTenantToDB(injector)
        await(injector.get[PocRepository].createPoc(poc))
        await(injector.get[PocAdminRepository].createPocAdmin(admin))
        await(injector.get[PocAdminStatusRepository].createStatus(createPocAdminStatus(admin, poc)))
      },
      responseAssertion = (body, _) => assert(body.contains(""""webIdentRequired":true"""))
    )
  }

  "Endpoint PUT /poc-admin/:id/active/:isActive" should {
    "deactivate, and activate user" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val keycloakUserService = i.get[KeycloakUserService]
      val tenant = addTenantToDB(i)
      val poc = addPocToDb(tenant, i.get[PocTable])
      val realm = CertifyKeycloak.defaultRealm
      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        realm,
        KeycloakTestData.createNewCertifyKeycloakUser(),
        CertifyKeycloak))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val pocAdmin = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        certifyUserId = Some(certifyUserId.value),
        status = Completed)
      val id = await(repository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id/active/0",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        body shouldBe empty
        await(repository.getPocAdmin(id)).value.active shouldBe false
        await(
          keycloakUserService.getUserById(
            realm,
            UserId(certifyUserId.value),
            CertifyKeycloak)).value.isEnabled shouldBe false
      }

      put(
        s"/poc-admin/$id/active/1",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        body shouldBe empty
        await(repository.getPocAdmin(id)).value.active shouldBe true
        await(
          keycloakUserService.getUserById(
            realm,
            UserId(certifyUserId.value),
            CertifyKeycloak)).value.isEnabled shouldBe true
      }
    }

    "fail deactivating employee, when employee not completed" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val keycloakUserService = i.get[KeycloakUserService]
      val tenant = addTenantToDB(i)
      val poc = addPocToDb(tenant, i.get[PocTable])
      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        CertifyKeycloak.defaultRealm,
        KeycloakTestData.createNewCertifyKeycloakUser(),
        CertifyKeycloak))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id, certifyUserId = Some(certifyUserId.value))
      val id = await(repository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id/active/0",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        body.contains(s"Poc admin with id '$id' cannot be de/-activated before status is Completed.") shouldBe true
        await(repository.getPocAdmin(id)).value.active shouldBe true
        await(
          keycloakUserService.getUserById(
            CertifyKeycloak.defaultRealm,
            UserId(certifyUserId.value),
            CertifyKeycloak)).value.isEnabled shouldBe true
      }
    }

    "return 404 when poc-admin does not exist" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val tenant = addTenantToDB(i)
      val invalidPocAdminId = UUID.randomUUID()

      put(
        s"/poc-admin/$invalidPocAdminId/active/0",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc admin with id '$invalidPocAdminId' not found"))
      }
    }

    "return 400 when isActive is invalid value" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val tenant = addTenantToDB(i)
      val invalidPocAdminId = UUID.randomUUID()

      put(
        s"/poc-admin/$invalidPocAdminId/active/2",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(400)
        assert(body.contains("Illegal value for ActivateSwitch: 2. Expected 0 or 1"))
      }
    }

    "return 409 when poc-admin does not have certifyUserId" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val tenant = addTenantToDB(i)
      val poc = addPocToDb(tenant, i.get[PocTable])
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id, certifyUserId = None, status = Completed)
      val id = await(repository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id/active/1",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc admin '$id' does not have certifyUserId"))
      }
    }

    "return 401 when poc of poc-admin doesn't belong to tenant " in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val tenant = addTenantToDB(i)
      val poc = addPocToDb(tenant, i.get[PocTable])
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id, certifyUserId = None)
      val id = await(repository.createPocAdmin(pocAdmin))

      val tenantTable = i.get[TenantTable]
      val unrelatedTenant = createTenant("unrelated tenant")
      await(tenantTable.createTenant(unrelatedTenant), 5.seconds)

      put(
        s"/poc-admin/$id/active/0",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(unrelatedTenant.tenantName).prepare,
          FakeX509Certs.validX509Header)
      ) {
        status should equal(401)
        assert(body.contains(s"Poc admin with id '$id' doesn't belong to requesting tenant admin."))
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = PUT,
      path = s"/poc-admin/$pocAdminId/active/0",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocAdmin](
      method = PUT,
      path = s"/poc-admin/$pocAdminId/active/0",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = createPocAdmin(
        pocAdminId = pocAdminId,
        pocId = poc1id,
        tenantId = TenantId(TenantName(globalTenantName)),
        status = Completed,
        certifyUserId = Some(TestData.PocAdmin.certifyUserId)
      ),
      before = (injector, admin) => {
        addTenantToDB(injector)
        val poc = createPoc(id = poc1id, tenantName = TenantName(globalTenantName))
        await(injector.get[PocRepository].createPoc(poc))
        await(injector.get[PocAdminRepository].createPocAdmin(admin))
      },
      responseAssertion = (body, _) => assert(body.isEmpty)
    )
  }

  "Endpoint DELETE /poc-admin/:id/2fa-token" should {
    "delete 2FA token for poc admin" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val clock = i.get[Clock]
      val repository = i.get[PocAdminRepository]
      val keycloakUserService = i.get[KeycloakUserService]
      val tenant = addTenantToDB(i)
      val poc = addPocToDb(tenant, i.get[PocTable])
      val instance = CertifyKeycloak
      val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
        CertifyKeycloak.defaultRealm,
        KeycloakTestData.createNewCertifyKeycloakUser(),
        instance,
        List(UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)
      ))
        .fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
      val pocAdmin = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        certifyUserId = Some(certifyUserId.value),
        status = Completed)
      val id = await(repository.createPocAdmin(pocAdmin))
      val getPocAdmin = repository.getPocAdmin(id)

      val requiredAction = for {
        requiredAction <-
          keycloakUserService.getUserById(CertifyKeycloak.defaultRealm, certifyUserId, instance).flatMap {
            case Some(ur) => Task.pure(ur.getRequiredActions)
            case None     => Task.raiseError(new RuntimeException("User not found"))
          }
      } yield requiredAction

      delete(
        s"/poc-admin/$id/2fa-token",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200)
        body shouldBe empty
        await(requiredAction) should contain theSameElementsAs List("webauthn-register", "UPDATE_PASSWORD")
        await(getPocAdmin).value.webAuthnDisconnected shouldBe Some(new DateTime(
          clock.instant().toString,
          DateTimeZone.forID(clock.getZone.getId)))
      }
    }

    "return 404 when poc-admin does not exist" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val tenant = addTenantToDB(i)
      val invalidPocAdminId = UUID.randomUUID()

      delete(
        s"/poc-admin/$invalidPocAdminId/2fa-token",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc admin with id '$invalidPocAdminId' not found"))
      }
    }

    "return 404 when poc-admin is not owned by tenant-admin" in withInjector { implicit i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val tenant = addTenantToDB(i)
      val poc = addPocToDb(tenant, i.get[PocTable])
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id)
      val id = await(repository.createPocAdmin(pocAdmin))

      val otherTenant = addTenantToDB("otherTenant", i)

      delete(
        s"/poc-admin/$id/2fa-token",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(otherTenant.tenantName).prepare,
          FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(body.contains(s"Poc admin with id '$id' not found"))
      }
    }

    "return 409 when poc-admin does not have certifyUserId" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val tenant = addTenantToDB(i)
      val poc = addPocToDb(tenant, i.get[PocTable])
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id, certifyUserId = None, status = Completed)
      val id = await(repository.createPocAdmin(pocAdmin))

      delete(
        s"/poc-admin/$id/2fa-token",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc admin '$id' does not have certifyUserId"))
      }
    }

    "return 409 when poc-admin is not in Completed status" in withInjector { i =>
      val token = i.get[FakeTokenCreator]
      val repository = i.get[PocAdminRepository]
      val tenant = addTenantToDB(i)
      val poc = addPocToDb(tenant, i.get[PocTable])
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id, certifyUserId = None, status = Pending)
      val id = await(repository.createPocAdmin(pocAdmin))

      delete(
        s"/poc-admin/$id/2fa-token",
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc admin '$id' is in wrong status: 'Pending', required: 'Completed'"))
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = DELETE,
      path = s"/poc-admin/$pocAdminId/2fa-token",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocAdmin](
      method = DELETE,
      path = s"/poc-admin/$pocAdminId/2fa-token",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = createPocAdmin(
        pocAdminId = pocAdminId,
        pocId = poc1id,
        tenantId = TenantId(TenantName(globalTenantName)),
        status = Completed),
      before = (injector, admin) => {
        addTenantToDB(injector)
        val poc = createPoc(id = poc1id, tenantName = TenantName(globalTenantName))
        val keycloakUserService = injector.get[KeycloakUserService]
        val certifyUserId = await(keycloakUserService.createUserWithoutUserName(
          CertifyKeycloak.defaultRealm,
          KeycloakTestData.createNewCertifyKeycloakUser(),
          CertifyKeycloak,
          List(UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)
        )).fold(ue => fail(ue.getClass.getSimpleName), ui => ui)
        await(injector.get[PocRepository].createPoc(poc))
        await(injector.get[PocAdminRepository].createPocAdmin(admin.copy(certifyUserId = Some(certifyUserId.value))))
      },
      responseAssertion = (body, _) => assert(body.isEmpty)
    )
  }

  "Endpoint GET /poc-admin/:id" must {
    "return poc-admin for given id" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val pocAdminRepository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = createPoc(poc1id, tenant.tenantName)
      await(pocTable.createPoc(poc))
      val pocAdmin = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        webAuthnDisconnected = Some(DateTime.now()),
        webIdentId = Some(UUID.randomUUID().toString),
        webIdentInitiateId = Some(UUID.randomUUID())
      )
      val id = await(pocAdminRepository.createPocAdmin(pocAdmin))
      val pocAdminFromTable = await(pocAdminRepository.getPocAdmin(id)).value

      get(
        s"/poc-admin/$id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(200)
        assertPocAdminJson(body)
          .hasId(pocAdminFromTable.id)
          .hasFirstName(pocAdminFromTable.name)
          .hasLastName(pocAdminFromTable.surname)
          .hasPocName(poc.pocName)
          .hasEmail(pocAdminFromTable.email)
          .hasPhone(pocAdminFromTable.mobilePhone)
          .hasDateOfBirth(pocAdminFromTable.dateOfBirth.date)
          .hasActive(pocAdminFromTable.active)
          .hasStatus(pocAdminFromTable.status.toString.toUpperCase)
          .hasCreatedAt(pocAdminFromTable.created.dateTime)
          .hasRevokeTime(pocAdminFromTable.webAuthnDisconnected.value)
          .hasWebIdentSuccessId(pocAdminFromTable.webIdentId.value)
          .hasWebIdentInitiateId(pocAdminFromTable.webIdentInitiateId.value)
          .hasWebIdentRequired(pocAdminFromTable.webIdentRequired)
      }
    }

    "return 403 when requesting user is not a tenant-admin" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]

      get(
        s"/poc-admin/$poc1id",
        headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)) {
        status should equal(403)
        assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
      }
    }

    "return 404 when poc-admin does not exists" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val tenant = addTenantToDB(Injector)

      get(
        s"/poc-admin/$poc1id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(404)
        assert(body.contains(s"PoC Admin with id '$poc1id' does not exist"))
      }
    }

    "return 401 when poc-admin is not owned by tenant-admin" in withInjector { implicit Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val pocAdminRepository = Injector.get[PocAdminRepository]

      val tenant = addTenantToDB(Injector)
      val otherTenant = addTenantToDB("otherTenantName", Injector)
      val poc = createPoc(poc1id, otherTenant.tenantName)
      val _ = await(pocTable.createPoc(poc))
      val id = await(pocAdminRepository.createPocAdmin(createPocAdmin(tenantId = otherTenant.id, pocId = poc.id)))

      get(
        s"/poc-admin/$id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(401)
        assert(body.contains(s"PoC Admin with id '$id' does not belong to tenant with id '${tenant.id.value.value}'"))
      }
    }

    "return 400 when tenant-admin does not exists" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]

      get(
        s"/poc-admin/$pocAdminId",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare,
          FakeX509Certs.validX509Header)
      ) {
        status should equal(400)
        assert(
          body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = GET,
      path = s"/poc-admin/$pocAdminId",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocAdmin](
      method = GET,
      path = s"/poc-admin/$pocAdminId",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload =
        createPocAdmin(pocAdminId = pocAdminId, pocId = poc1id, tenantId = TenantId(TenantName(globalTenantName))),
      before = (injector, admin) => {
        addTenantToDB(injector)
        val poc = createPoc(id = poc1id, tenantName = TenantName(globalTenantName))
        await(injector.get[PocRepository].createPoc(poc))
        await(injector.get[PocAdminRepository].createPocAdmin(admin))
      },
      responseAssertion = (body, admin) => {
        assertPocAdminJson(body).hasId(admin.id)
      }
    )
  }

  "Endpoint PUT /poc-admin/:id" must {
    "update poc-admin for given id" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val pocAdminRepository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = createPoc(poc1id, tenant.tenantName)
      await(pocTable.createPoc(poc))
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id)
        .copy(
          status = Pending,
          webIdentRequired = true,
          webIdentInitiateId = None
        )
      val id = await(pocAdminRepository.createPocAdmin(pocAdmin))
      val pocAdminFromTable = await(pocAdminRepository.getPocAdmin(id)).value
      val updatePocAdmin = pocAdminFromTable.copy(
        name = "new name",
        surname = "new surname",
        email = "email@email.com",
        mobilePhone = "+46-498-313789",
        dateOfBirth = BirthDate(LocalDate.parse("1989-11-25"))
      )

      put(
        uri = s"/poc-admin/$id",
        body = pocAdminToFormattedPutPocAdminINJson(updatePocAdmin).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(200) withClue s"Error response: $body"
      }

      get(
        s"/poc-admin/$id",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
          FakeX509Certs.validX509Header)) {
        status should equal(200)
        assertPocAdminJson(body)
          .hasId(updatePocAdmin.id)
          .hasFirstName(updatePocAdmin.name)
          .hasLastName(updatePocAdmin.surname)
          .hasEmail(updatePocAdmin.email)
          .hasPhone(updatePocAdmin.mobilePhone)
          .hasDateOfBirth(updatePocAdmin.dateOfBirth.date)
      }
    }

    "return validation errors when invalid values are passed" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val pocAdminRepository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = createPoc(poc1id, tenant.tenantName)
      await(pocTable.createPoc(poc))
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id)
        .copy(
          status = Pending,
          webIdentRequired = true,
          webIdentInitiateId = None
        )
      val id = await(pocAdminRepository.createPocAdmin(pocAdmin))
      val pocAdminFromTable = await(pocAdminRepository.getPocAdmin(id)).value
      val updatePocAdmin = pocAdminFromTable.copy(
        name = "new name",
        surname = "new surname",
        email = "new email",
        mobilePhone = "59145678464",
        dateOfBirth = BirthDate(LocalDate.parse("1989-11-25"))
      )

      put(
        uri = s"/poc-admin/$id",
        body = pocAdminToFormattedPutPocAdminINJson(updatePocAdmin).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(400)
        val errorResponse = read[ValidationErrorsResponse](body)
        errorResponse.validationErrors should contain theSameElementsAs Seq(
          FieldError("email", "Invalid email address 'new email'"),
          FieldError("phone", "Invalid phone number '59145678464'")
        )
      }
    }

    "return 409 when poc-admin is in Completed status" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val pocAdminRepository = Injector.get[PocAdminRepository]
      val poc = createPoc(poc1id, tenant.tenantName)
      val _ = await(pocTable.createPoc(poc))
      val pocAdmin = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        status = Completed,
        webIdentRequired = true,
        webIdentInitiateId = None)
      val id = await(pocAdminRepository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id",
        body = pocAdminToFormattedPutPocAdminINJson(pocAdmin).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc admin '$id' is in wrong status: 'Completed'"))
      }
    }

    "return 409 when poc-admin is in Processing status" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val pocAdminRepository = Injector.get[PocAdminRepository]
      val poc = createPoc(poc1id, tenant.tenantName)
      val _ = await(pocTable.createPoc(poc))
      val pocAdmin = createPocAdmin(
        tenantId = tenant.id,
        pocId = poc.id,
        status = Processing,
        webIdentRequired = true,
        webIdentInitiateId = None)
      val id = await(pocAdminRepository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id",
        body = pocAdminToFormattedPutPocAdminINJson(pocAdmin).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc admin '$id' is in wrong status: 'Processing'"))
      }
    }

    "return 409 when poc-admin webIdentRequired is false" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val pocAdminRepository = Injector.get[PocAdminRepository]
      val poc = createPoc(poc1id, tenant.tenantName)
      val _ = await(pocTable.createPoc(poc))
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id)
        .copy(
          status = Pending,
          webIdentRequired = false,
          webIdentInitiateId = None
        )
      val id = await(pocAdminRepository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id",
        body = pocAdminToFormattedPutPocAdminINJson(pocAdmin).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc admin '$id' has webIdentRequired set to false"))
      }
    }

    "return 409 when poc-admin webIdentInitiateId is set" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val tenant = addTenantToDB(Injector)
      val pocAdminRepository = Injector.get[PocAdminRepository]
      val poc = createPoc(poc1id, tenant.tenantName)
      val _ = await(pocTable.createPoc(poc))
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id)
        .copy(
          status = Pending,
          webIdentRequired = true,
          webIdentInitiateId = Some(UUID.randomUUID())
        )
      val id = await(pocAdminRepository.createPocAdmin(pocAdmin))

      put(
        s"/poc-admin/$id",
        body = pocAdminToFormattedPutPocAdminINJson(pocAdmin).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(409)
        assert(body.contains(s"Poc admin '$id' webIdentInitiateId is set"))
      }
    }

    "return 403 when requesting user is not a tenant-admin" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]

      put(
        s"/poc-admin/${UUID.randomUUID()}",
        headers = Map("authorization" -> token.superAdmin.prepare, FakeX509Certs.validX509Header)) {
        status should equal(403)
        assert(body == "NOK(1.0,false,'AuthenticationError,Forbidden)")
      }
    }

    "return 404 when poc-admin does not exists" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val tenant = addTenantToDB(Injector)
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc1id)

      put(
        s"/poc-admin/${pocAdmin.id}",
        body = pocAdminToFormattedPutPocAdminINJson(pocAdmin).getBytes,
        headers =
          Map("authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare, FakeX509Certs.validX509Header)
      ) {
        status should equal(404)
        assert(body.contains(s"PoC admin with id '${pocAdmin.id}' does not exist"))
      }
    }

    "return 401 when poc-admin is not owned by tenant-admin" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]
      val pocTable = Injector.get[PocRepository]
      val pocAdminRepository = Injector.get[PocAdminRepository]
      val tenant = addTenantToDB(Injector)
      val poc = createPoc(poc1id, tenant.tenantName)
      val _ = await(pocTable.createPoc(poc))
      val pocAdmin = createPocAdmin(tenantId = tenant.id, pocId = poc.id).copy(status = Processing)
      val id = await(pocAdminRepository.createPocAdmin(pocAdmin))

      val otherTenant = addTenantToDB("otherTenantName", Injector)

      put(
        s"/poc-admin/$id",
        body = pocAdminToFormattedPutPocAdminINJson(pocAdmin).getBytes,
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(otherTenant.tenantName).prepare,
          FakeX509Certs.validX509Header)
      ) {
        status should equal(401)
        assert(
          body.contains(s"PoC admin with id '$id' does not belong to tenant with id '${otherTenant.id.value.value}'"))
      }
    }

    "return 400 when tenant-admin does not exists" in withInjector { Injector =>
      val token = Injector.get[FakeTokenCreator]

      put(
        s"/poc-admin/${UUID.randomUUID()}",
        headers = Map(
          "authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare,
          FakeX509Certs.validX509Header)
      ) {
        status should equal(400)
        assert(
          body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = PUT,
      path = s"/poc-admin/$pocAdminId",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[PocAdmin](
      method = PUT,
      path = s"/poc-admin/$pocAdminId",
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload =
        createPocAdmin(pocAdminId = pocAdminId, pocId = poc1id, tenantId = TenantId(TenantName(globalTenantName)))
          .copy(
            status = Pending,
            webIdentRequired = true,
            webIdentInitiateId = None
          ),
      before = (injector, admin) => {
        addTenantToDB(injector)
        val poc = createPoc(id = poc1id, tenantName = TenantName(globalTenantName))
        await(injector.get[PocRepository].createPoc(poc))
        await(injector.get[PocAdminRepository].createPocAdmin(admin))
      },
      requestBody = admin => pocAdminToFormattedPutPocAdminINJson(admin.copy(name = "Bruce Wayne")),
      responseAssertion = (body, _) => body.isEmpty
    )
  }

  "Endpoint POST /poc-admin/create" must {
    val EndPoint = "/poc-admin/create"
    "successfully create poc admin" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val tenant = addTenantToDB(injector)
        val pocAdminTable = injector.get[PocAdminRepository]
        val pocAdminStatusTable = injector.get[PocAdminStatusRepository]
        val poc = createPoc(pocId, tenant.tenantName)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)
        val createPocAdminRequest = CreatePocAdminRequest(
          poc.id,
          "first",
          "last",
          "test@ubirch.com",
          "+4911111111",
          LocalDate.now().minusYears(30),
          true
        )

        val requestBody = pocAdminToFormattedCreatePocAdminJson(createPocAdminRequest)
        post(
          EndPoint,
          body = requestBody.getBytes(),
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(200)

          val pocAdmins = pocAdminTable.getByPocId(poc.id).runSyncUnsafe(5.seconds)
          pocAdmins.size shouldBe 1
          val pocAdmin = pocAdmins.head
          val pocAdminStatus = pocAdminStatusTable.getStatus(pocAdmin.id).runSyncUnsafe(5.seconds)

          pocAdmin.pocId shouldBe createPocAdminRequest.pocId
          pocAdmin.name shouldBe createPocAdminRequest.firstName
          pocAdmin.surname shouldBe createPocAdminRequest.lastName
          pocAdmin.email shouldBe createPocAdminRequest.email
          pocAdmin.dateOfBirth.date shouldBe createPocAdminRequest.dateOfBirth
          pocAdmin.webIdentRequired shouldBe createPocAdminRequest.webIdentRequired

          assert(pocAdminStatus.isDefined)
        }
      }
    }

    "return 404 when poc does not exists" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val tenant = addTenantToDB(injector)
        val createPocAdminRequest = CreatePocAdminRequest(
          UUID.randomUUID(),
          "first",
          "last",
          "test@ubirch.com",
          "+4911111111",
          LocalDate.now().minusYears(30),
          true
        )

        post(
          EndPoint,
          body = pocAdminToFormattedCreatePocAdminJson(createPocAdminRequest).getBytes,
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(tenant.tenantName).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(404)
          assert(body.contains(s"pocId is not found: ${createPocAdminRequest.pocId}"))
        }
      }
    }

    "return 400 when tenant-admin does not exists" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]

        post(
          EndPoint,
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare,
            FakeX509Certs.validX509Header)) {
          status should equal(400)
          assert(
            body == s"NOK(1.0,false,'AuthenticationError,couldn't find tenant in db for ${TENANT_GROUP_PREFIX}tenantName)")
        }
      }
    }

    "return 400 when incoming request is invalid" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)
        val createPocAdminRequest = CreatePocAdminRequest(
          poc.id,
          "",
          "",
          "invalid_email",
          "1111111",
          LocalDate.now().minusYears(30),
          true
        )

        post(
          EndPoint,
          body = pocAdminToFormattedCreatePocAdminJson(createPocAdminRequest).getBytes,
          headers = Map(
            "authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare,
            FakeX509Certs.validX509Header)
        ) {
          status should equal(400)
          assert(
            body.contains("the input data is invalid. name is invalid; surName is invalid; email is invalid; phone number is invalid")
          )
        }
      }
    }

    x509ForbiddenWhenHeaderIsInvalid(
      method = POST,
      path = EndPoint,
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      before = injector => addTenantToDB(injector)
    )

    x509SuccessWhenNonBlockingIssuesWithCert[CreatePocAdminRequest](
      method = POST,
      path = EndPoint,
      createToken = _.userOnDevicesKeycloak(TenantName(globalTenantName)),
      payload = CreatePocAdminRequest(
        pocId = poc1id,
        firstName = "first",
        lastName = "last",
        email = "test@ubirch.com",
        phone = "+4911111111",
        dateOfBirth = LocalDate.now().minusYears(30),
        webIdentRequired = true
      ),
      before = (injector, _) => {
        addTenantToDB(injector)
        val poc = createPoc(id = poc1id, tenantName = TenantName(globalTenantName))
        await(injector.get[PocRepository].createPoc(poc))
      },
      requestBody = r => pocAdminToFormattedCreatePocAdminJson(r),
      responseAssertion = (body, _) => body.isEmpty
    )
  }

  "Endpoint POST /poc/retry/:id" must {
    "reset CreationAttempts counter and move PoC and all related aborted PoC Admins to Processing status" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminTable]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName).copy(creationAttempts = 10, status = Aborted)
        val pocAdminAborted1 =
          createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Aborted).copy(creationAttempts = 10)
        val pocAdminAborted2 =
          createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Aborted).copy(creationAttempts = 10)
        val pocAdminNotAborted =
          createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Completed).copy(creationAttempts = 6)
        val pocAdminDiffTenant =
          createPocAdmin(tenantId = TenantId(TenantName("diffTenant")), pocId = poc.id, status = Aborted).copy(
            creationAttempts = 10)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)
        pocAdminTable.createPocAdmin(pocAdminAborted1).runSyncUnsafe(5.seconds)
        pocAdminTable.createPocAdmin(pocAdminAborted2).runSyncUnsafe(5.seconds)
        pocAdminTable.createPocAdmin(pocAdminNotAborted).runSyncUnsafe(5.seconds)
        pocAdminTable.createPocAdmin(pocAdminDiffTenant).runSyncUnsafe(5.seconds)

        put(
          s"/poc/retry/$pocId",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare)
        ) {
          status should equal(200)
          body should be(empty)
        }

        val updatedPoc = await(pocTable.getPoc(pocId), 5.seconds).value
        updatedPoc.status shouldBe Processing
        updatedPoc.creationAttempts shouldBe 0

        val pocAdminUpdated1 = await(pocAdminTable.getPocAdmin(pocAdminAborted1.id)).value
        pocAdminUpdated1.status shouldBe Processing
        pocAdminUpdated1.creationAttempts shouldBe 0
        val pocAdminUpdated2 = await(pocAdminTable.getPocAdmin(pocAdminAborted2.id)).value
        pocAdminUpdated2.status shouldBe Processing
        pocAdminUpdated2.creationAttempts shouldBe 0
        val pocAdminUpdated3 = await(pocAdminTable.getPocAdmin(pocAdminDiffTenant.id)).value
        pocAdminUpdated3.status shouldBe Processing
        pocAdminUpdated3.creationAttempts shouldBe 0
        val pocAdminNotUpdated1 = await(pocAdminTable.getPocAdmin(pocAdminNotAborted.id)).value
        pocAdminNotUpdated1.status shouldBe Completed
        pocAdminNotUpdated1.creationAttempts shouldBe 6
      }
    }

    "fail with NotFound if poc can't be find by id" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName).copy(creationAttempts = 10, status = Aborted)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)

        val randomUUID = UUID.randomUUID()
        put(
          s"/poc/retry/$randomUUID",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare)
        ) {
          status should equal(404)
          assert(body.contains(s"PoC not found by id: $randomUUID"))
        }

        val updatedPoc = await(pocTable.getPoc(pocId), 5.seconds).value
        updatedPoc.status shouldBe Aborted
        updatedPoc.creationAttempts shouldBe 10
      }
    }

    val badStatuses = Table("status", Pending, Processing, Completed)

    forAll(badStatuses) { badStatus =>
      s"fail with BadRequest if Poc is in different state than Aborted ($badStatus)" in {
        withInjector { injector =>
          val token = injector.get[FakeTokenCreator]
          val pocId = UUID.randomUUID()
          val pocTable = injector.get[PocRepository]
          val tenant = addTenantToDB(injector)
          val poc = createPoc(pocId, tenant.tenantName).copy(creationAttempts = 10, status = badStatus)
          pocTable.createPoc(poc).runSyncUnsafe(5.seconds)

          put(
            s"/poc/retry/$pocId",
            headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare)
          ) {
            status should equal(400)
            assert(body.contains(s"PoC should be in Aborted status but is in $badStatus"))
          }

          val updatedPoc = await(pocTable.getPoc(pocId), 5.seconds).value
          updatedPoc.status shouldBe badStatus
          updatedPoc.creationAttempts shouldBe 10
        }
      }
    }
  }

  "Endpoint POST /poc/poc-admin/retry/:id" must {
    "reset CreationAttempts counter and move PoC Admin to Processing status" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminTable]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin =
          createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Aborted).copy(creationAttempts = 10)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)
        pocAdminTable.createPocAdmin(pocAdmin).runSyncUnsafe(5.seconds)

        put(
          s"/poc/poc-admin/retry/${pocAdmin.id}",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare)
        ) {
          status should equal(200)
          body should be(empty)
        }

        val updatedPocAdmin = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds).value
        updatedPocAdmin.status shouldBe Processing
        updatedPocAdmin.creationAttempts shouldBe 0
      }
    }

    "fail with NotFound if PoC admin can't be find by id" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminTable]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin =
          createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Aborted).copy(creationAttempts = 10)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)
        pocAdminTable.createPocAdmin(pocAdmin).runSyncUnsafe(5.seconds)

        val randomUUID = UUID.randomUUID()
        put(
          s"/poc/poc-admin/retry/$randomUUID",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare)
        ) {
          status should equal(404)
          assert(body.contains(s"Could not find PoC Admin with id: $randomUUID"))
        }

        val updatedPocAdmin = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds).value
        updatedPocAdmin.status shouldBe Aborted
        updatedPocAdmin.creationAttempts shouldBe 10
      }
    }

    "fail with NotFound if PoC Admin is assigned to different tenant" in {
      withInjector { injector =>
        val token = injector.get[FakeTokenCreator]
        val pocId = UUID.randomUUID()
        val pocTable = injector.get[PocRepository]
        val pocAdminTable = injector.get[PocAdminTable]
        val tenant = addTenantToDB(injector)
        val poc = createPoc(pocId, tenant.tenantName)
        val pocAdmin =
          createPocAdmin(tenantId = TenantId(TenantName("randomName")), pocId = poc.id, status = Aborted).copy(
            creationAttempts = 10)
        pocTable.createPoc(poc).runSyncUnsafe(5.seconds)
        pocAdminTable.createPocAdmin(pocAdmin).runSyncUnsafe(5.seconds)

        put(
          s"/poc/poc-admin/retry/${pocAdmin.id}",
          headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare)
        ) {
          status should equal(404)
          assert(body.contains(s"PoC Admin with id ${pocAdmin.id} is assigned to different tenant than ${tenant.id}"))
        }

        val updatedPocAdmin = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds).value
        updatedPocAdmin.status shouldBe Aborted
        updatedPocAdmin.creationAttempts shouldBe 10
      }
    }

    val badStatuses = Table("status", Pending, Processing, Completed)

    forAll(badStatuses) { badStatus =>
      s"fail with BadRequest if PoC Admin is in different state than Aborted ($badStatus)" in {
        withInjector { injector =>
          val token = injector.get[FakeTokenCreator]
          val pocId = UUID.randomUUID()
          val pocTable = injector.get[PocRepository]
          val pocAdminTable = injector.get[PocAdminTable]
          val tenant = addTenantToDB(injector)
          val poc = createPoc(pocId, tenant.tenantName)
          val pocAdmin =
            createPocAdmin(tenantId = tenant.id, pocId = poc.id, status = Aborted).copy(
              creationAttempts = 10,
              status = badStatus)
          pocTable.createPoc(poc).runSyncUnsafe(5.seconds)
          pocAdminTable.createPocAdmin(pocAdmin).runSyncUnsafe(5.seconds)

          put(
            s"/poc/poc-admin/retry/${pocAdmin.id}",
            headers = Map("authorization" -> token.userOnDevicesKeycloak(TenantName(globalTenantName)).prepare)
          ) {
            status should equal(400)
            assert(body.contains(s"Expected PoC Admin to be in Aborted status but instead it is in $badStatus"))
          }

          val updatedPocAdmin = await(pocAdminTable.getPocAdmin(pocAdmin.id), 5.seconds).value
          updatedPocAdmin.status shouldBe badStatus
          updatedPocAdmin.creationAttempts shouldBe 10
        }
      }
    }
  }

  def pocToFormattedJson(poc: Poc): String = {
    import poc._
    val json = s"""{
                  |  "id" : "$id",
                  |  "tenantId" : "${tenantId.value.value}",
                  |  "externalId" : "$externalId",
                  |  "pocType" : "$pocType",
                  |  "pocName" : "$pocName",
                  |  "address" : {
                  |    "street" : "${address.street}",
                  |    "houseNumber" : "${address.houseNumber}",
                  |    "zipcode" : ${address.zipcode},
                  |    "city" : "${address.city}",
                  |    "country" : "${address.country}"
                  |  },
                  |  "phone" : "${poc.phone}",
                  |  "extraConfig" : {
                  |    "test" : "hello"
                  |  },
                  |  "manager" : {
                  |    "lastName" : "${manager.managerSurname}",
                  |    "firstName" : "${manager.managerName}",
                  |    "email" : "${manager.managerEmail}",
                  |    "mobilePhone" : "${manager.managerMobilePhone}"
                  |  },
                  |  "roleName" : "$roleName",
                  |  "deviceId" : "$deviceId",
                  |  "status" : "${poc.status.toString.toUpperCase}",
                  |  "creationAttempts": ${poc.creationAttempts},
                  |  "lastUpdated" : "${lastUpdated.dateTime.toInstant}",
                  |  "created" : "${created.dateTime.toInstant}"
                  |}""".stripMargin
    pretty(render(parse(json)))
  }

  def pocAdminToFormattedPutPocAdminINJson(pa: PocAdmin): String = {
    val json = s"""{
                  | "firstName" : "${pa.name}",
                  | "lastName" : "${pa.surname}",
                  | "dateOfBirth" : {
                  |   "year" : ${pa.dateOfBirth.date.year().get()},
                  |   "month" : ${pa.dateOfBirth.date.monthOfYear().get()},
                  |   "day" : ${pa.dateOfBirth.date.dayOfMonth().get()}
                  |  },
                  |  "email" : "${pa.email}",
                  |  "phone" : "${pa.mobilePhone}"
                  |}""".stripMargin
    pretty(render(parse(json)))
  }

  def pocAdminToFormattedCreatePocAdminJson(createPocAdminRequest: CreatePocAdminRequest): String = {
    val json = s"""{
                  | "pocId": "${createPocAdminRequest.pocId}",
                  | "firstName" : "${createPocAdminRequest.firstName}",
                  | "lastName" : "${createPocAdminRequest.lastName}",
                  | "dateOfBirth" : {
                  |   "year" : ${createPocAdminRequest.dateOfBirth.year().get()},
                  |   "month" : ${createPocAdminRequest.dateOfBirth.monthOfYear().get()},
                  |   "day" : ${createPocAdminRequest.dateOfBirth.dayOfMonth().get()}
                  |  },
                  |  "email" : "${createPocAdminRequest.email}",
                  |  "phone" : "${createPocAdminRequest.phone}",
                  |  "webIdentRequired": ${createPocAdminRequest.webIdentRequired}
                  |}""".stripMargin
    pretty(render(parse(json)))
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

  override protected def beforeAll: Unit = {
    super.beforeAll()
    withInjector { injector =>
      lazy val tenantAdminController = injector.get[TenantAdminController]
      addServlet(tenantAdminController, "/*")
    }
  }

  private def addTenantToDB(injector: InjectorHelper): Tenant = {
    addTenantToDB(globalTenantName, injector)
  }

  private def addTenantToDB(name: String = globalTenantName, injector: InjectorHelper): Tenant = {
    val tenantTable = injector.get[TenantTable]
    val tenant = createTenant(name = name)
    await(tenantTable.createTenant(tenant), 5.seconds)
    tenant
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

  implicit val pocAdminOrdering: Ordering[PocAdmin] =
    (x: PocAdmin, y: PocAdmin) => x.surname.compareTo(y.surname)
}
