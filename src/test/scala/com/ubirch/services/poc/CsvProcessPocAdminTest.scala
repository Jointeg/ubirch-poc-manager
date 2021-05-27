package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.{ createTenant, pocTypeValue }
import com.ubirch.UnitTestBase
import com.ubirch.controllers.TenantAdminContext
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, PocStatusRepository }
import com.ubirch.services.poc.util.CsvConstants.pocAdminHeaderLine

import java.util.UUID

class CsvProcessPocAdminTest extends UnitTestBase {

  private val pocId = UUID.randomUUID()

  private val invalidHeader =
    s"""external_id_invalid*;poc_type*;poc_name*;street*;street_number*;additional_address;zipcode*;city*;county;federal_state;country*;phone*;certify_app*;logo_url;client_cert*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config;technician_surname;technician_name*;technician_email*;technician_mobile_phone*;technician_date_of_birth*;web_ident_required*\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;$pocTypeValue;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]};Mustermann;Herr;herr.mustermann@mail.de;0176-543;01.01.1971;TRUE"""

  private val validCsv =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971;TRUE
       |""".stripMargin

  private val validHeaderButBadCsvRows =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};;Herr;herr.mustermann@;0176-738786782;01.01.1971;xfalse
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;017782;01.1971;TRUE
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971;TRUE
       |""".stripMargin

  private val tenant = createTenant()
  private val tenantContext = TenantAdminContext(UUID.randomUUID(), tenant.id.value.asJava())

  "ProcessPoc" should {
    "create poc, admin and status" in {
      withInjector { injector =>
        val processPocAdmin = injector.get[CsvProcessPocAdmin]
        val pocRepository = injector.get[PocRepository]
        val pocStatusRepository = injector.get[PocStatusRepository]
        val pocAdminRepository = injector.get[PocAdminRepository]
        val pocAdminStatusRepository = injector.get[PocAdminStatusRepository]

        (for {
          result <- processPocAdmin.createListOfPoCsAndAdmin(validCsv, tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          poc = pocs.head
          pocStatusOpt <- pocStatusRepository.getPocStatus(poc.id)
          pocAdmins <- pocAdminRepository.getAllPocAdminsByTenantId(tenant.id)
          pocAdmin = pocAdmins.head
          pocAdminStatusOpt <- pocAdminStatusRepository.getStatus(pocAdmin.id)
        } yield {
          assert(result.isRight)
          assert(pocs.length == 1)
          assert(pocStatusOpt.isDefined)
          assert(pocStatusOpt.get.pocId == poc.id)
          assert(pocAdmins.length == 1)
          assert(pocAdminStatusOpt.isDefined)
          assert(pocAdminStatusOpt.get.pocAdminId == pocAdmin.id)
        }).onErrorHandle { e =>
          fail(e)
        }.runSyncUnsafe()
      }
    }

    "fail to create create poc, admin and status when the header is invalid" in {
      withInjector { injector =>
        val processPocAdmin = injector.get[CsvProcessPocAdmin]
        val pocRepository = injector.get[PocRepository]
        val pocAdminRepository = injector.get[PocAdminRepository]

        (for {
          result <- processPocAdmin.createListOfPoCsAndAdmin(invalidHeader, tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          pocAdmins <- pocAdminRepository.getAllPocAdminsByTenantId(tenant.id)
        } yield {
          result.left.get.shouldBe("external_id_invalid* didn't equal expected header external_id*; the right header order would be: external_id*,poc_type*,poc_name*,street*,street_number*,additional_address,zipcode*,city*,county,federal_state,country*,phone*,certify_app*,logo_url,client_cert*,manager_surname*,manager_name*,manager_email*,manager_mobile_phone*,extra_config")
          assert(pocs.isEmpty)
          assert(pocAdmins.isEmpty)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }

    "success to create poc, admin and status for only valid rows" in {
      withInjector { injector =>
        val processPocAdmin = injector.get[CsvProcessPocAdmin]
        val pocRepository = injector.get[PocRepository]
        val pocAdminRepository = injector.get[PocAdminRepository]

        (for {
          result <- processPocAdmin.createListOfPoCsAndAdmin(validHeaderButBadCsvRows, tenant, tenantContext)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          pocAdmins <- pocAdminRepository.getAllPocAdminsByTenantId(tenant.id)
        } yield {
          assert(result.isLeft)
          assert(pocs.length == 1)
          assert(pocAdmins.length == 1)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }
  }
}
