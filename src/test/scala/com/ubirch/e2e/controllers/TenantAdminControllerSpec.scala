package com.ubirch.e2e.controllers

import com.ubirch.FakeTokenCreator
import com.ubirch.controllers.TenantAdminController
import com.ubirch.db.tables.{PocRepository, PocStatusRepository}
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.poc.PocStatus
import com.ubirch.services.formats.DomainObjectFormats
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.poc.util.CsvConstants
import com.ubirch.services.poc.util.CsvConstants.headerLine
import com.ubirch.services.{DeviceKeycloak, UsersKeycloak}
import io.prometheus.client.CollectorRegistry
import org.json4s.ext.{JavaTypesSerializers, JodaTimeSerializers}
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.util.UUID
import scala.concurrent.duration.DurationInt

class TenantAdminControllerSpec extends E2ETestBase with BeforeAndAfterEach with BeforeAndAfterAll {

  private val pocId: UUID = UUID.randomUUID()
  implicit private val formats: Formats =
    DefaultFormats.lossless ++ DomainObjectFormats.all ++ JavaTypesSerializers.all ++ JodaTimeSerializers.all

  private val badCsv =
    "poc_id*;poc_name*;poc_street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;client_cert;data_schema_id*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12A636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12A636;Wunschstadt;;;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}"

  private val goodCsv =
    s"""$headerLine
      |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  "Endpoint POST pocs/create" must {

    //Todo: Fix authorization
    "return success without invalid rows" in {
      withInjector { Injector =>

        val token = Injector.get[FakeTokenCreator]

        post(
          "/pocs/create",
          body = goodCsv.getBytes(),
          headers = Map("authorization" -> token.userOnDevicesKeycloak.prepare)) {
          status should equal(200)
          assert(body.isEmpty)
        }
        val repo = Injector.get[PocRepository]
        val res = for {
          data <- repo.getAllPocs()
        } yield data
        val result = await(res, 5.seconds)
        result.map(_.externalId shouldBe pocId.toString)
      }

    }

    "return invalid csv rows" in {
      withInjector { Injector =>

        val token = Injector.get[FakeTokenCreator]

        post(
          "/pocs/create",
          body = badCsv.getBytes(),
          headers = Map("authorization" -> token.userOnDevicesKeycloak.prepare)) {
          status should equal(200)
          assert(body == CsvConstants.headerErrorMsg("poc_id*", CsvConstants.externalId))
        }
      }
    }
  }

  private def createPocStatus(id: UUID = UUID.randomUUID()): PocStatus =
    PocStatus(
      id,
      validDataSchemaGroup = true,
      clientCertRequired = false,
      clientCertDownloaded = None,
      clientCertProvided = None,
      logoRequired = false,
      logoReceived = None,
      logoStored = None
    )

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

        get(s"/pocStatus/${pocStatus.pocId}", headers = Map("authorization" -> token.userOnDevicesKeycloak.prepare)) {
          status should equal(200)
          assert(body == write[PocStatus](storedStatus))
        }
      }
    }

    "return resource not found error" in {
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
        val randomID = UUID.randomUUID()

        get(s"/pocStatus/$randomID", headers = Map("authorization" -> token.userOnDevicesKeycloak.prepare)) {
          status should equal(404)
          assert(body == s"NOK(1.0,false,'ResourceNotFoundError,pocStatus with $randomID couldn't be found)")
        }
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

}
