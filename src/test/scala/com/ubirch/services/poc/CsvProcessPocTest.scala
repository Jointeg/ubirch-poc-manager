package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository }
import com.ubirch.services.poc.util.CsvConstants.pocHeaderLine

import java.util.UUID

class CsvProcessPocTest extends UnitTestBase {

  private val pocId = UUID.randomUUID()

  // header has wrong names
  private val invalidHeader =
    "poc_id*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;client_cert;data_schema_id*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]}"

  private val validCsv =
    s"""$pocHeaderLine
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val validHeaderButBadCsvRows =
    s"""$pocHeaderLine
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;Xfalse;certification-vaccination;Musterfrau;Frau;frau.musterfraumail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val tenant = createTenant()

  "ProcessPoc" should {
    "create poc and pocStatus" in {
      withInjector { injector =>
        val processPoc = injector.get[CsvProcessPoc]
        val pocRepository = injector.get[PocRepository]
        val pocStatusRepository = injector.get[PocStatusRepository]

        (for {
          result <- processPoc.createListOfPoCs(validCsv, tenant)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          poc = pocs.head
          pocStatusOpt <- pocStatusRepository.getPocStatus(poc.id)
        } yield {
          assert(result.isRight)
          assert(pocs.length == 1)
          assert(pocStatusOpt.isDefined)
          assert(pocStatusOpt.get.pocId == poc.id)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }

    "fail to create poc and pocStatus when the header is invalid" in {
      withInjector { injector =>
        val processPoc = injector.get[CsvProcessPoc]
        val pocRepository = injector.get[PocRepository]

        (for {
          result <- processPoc.createListOfPoCs(invalidHeader, tenant)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
        } yield {
          result.left.get.shouldBe("poc_id* didn't equal expected header external_id*; the right header order would be: external_id*,poc_name*,street*,street_number*,additional_address,zipcode*,city*,county,federal_state,country*,phone*,certify_app*,logo_url,client_cert*,data_schema_id*,manager_surname*,manager_name*,manager_email*,manager_mobile_phone*,extra_config")
          assert(pocs.isEmpty)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }

    "success to create poc and pocStatus for only valid rows" in {
      withInjector { injector =>
        val processPoc = injector.get[CsvProcessPoc]
        val pocRepository = injector.get[PocRepository]

        (for {
          result <- processPoc.createListOfPoCs(validHeaderButBadCsvRows, tenant)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
        } yield {
          assert(result.isLeft)
          assert(pocs.length == 2)
        }).onErrorHandle(e => fail(e)).runSyncUnsafe()
      }
    }
  }
}
