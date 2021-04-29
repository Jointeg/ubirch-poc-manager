package com.ubirch.e2e.controllers

import com.ubirch.FakeTokenCreator
import com.ubirch.controllers.TenantAdminController
import com.ubirch.db.tables.PocRepository
import com.ubirch.e2e.E2ETestBase
import com.ubirch.services.{DeviceKeycloak, UsersKeycloak}
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.poc.util.CsvConstants
import com.ubirch.services.poc.util.CsvConstants.headerLine
import io.prometheus.client.CollectorRegistry
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import java.util.UUID
import scala.concurrent.duration.DurationInt

class TenantAdminControllerSpec extends E2ETestBase with BeforeAndAfterEach with BeforeAndAfterAll {

  private val pocId: UUID = UUID.randomUUID()

  private val badCsv =
    "poc_id*;poc_name*;poc_street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;client_cert;data_schema_id*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12A636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12A636;Wunschstadt;;;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}"

  private val goodCsv =
    s"""$headerLine
      |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  "Tenant Admin Controller" must {

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
