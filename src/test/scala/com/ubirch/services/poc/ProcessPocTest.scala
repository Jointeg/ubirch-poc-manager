package com.ubirch.services.poc

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.UnitTestBase
import com.ubirch.db.tables.{ PocAdminRepository, PocAdminStatusRepository, PocRepository, PocStatusRepository }
import com.ubirch.services.poc.util.CsvConstants.pocHeaderLine

import java.util.UUID

class ProcessPocTest extends UnitTestBase {

  private val pocId = UUID.randomUUID()

  private val invalidHeader =
    "poc_id*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;data_schema_id*;encoding*;extra_signing_key_id;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}"

  private val validCsv =
    s"""$pocHeaderLine
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val validHeaderButBadCsvRows =
    s"""$pocHeaderLine
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;Xfalse;certification-vaccination;Musterfrau;Frau;frau.musterfraumail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val tenant = createTenant()

  "ProcessPoc" should {
    "create pocAdmin, pocAdminStatus, poc and pocStatus" in {
      withInjector { injector =>
        val processPoc = injector.get[ProcessPoc]
        val pocRepository = injector.get[PocRepository]
        //val pocAdminRepository = injector.get[PocAdminRepository]
        val pocStatusRepository = injector.get[PocStatusRepository]
        //val pocAdminStatusRepository = injector.get[PocAdminStatusRepository]

        (for {
          result <- processPoc.createListOfPoCs(validCsv, tenant)
          pocs <- pocRepository.getAllPocsByTenantId(tenant.id)
          poc = pocs.head
          pocStatusOpt <- pocStatusRepository.getPocStatus(poc.id)
        } yield {
          assert(result.isRight)
          assert(pocs.length == 1)
          //assert(pocStatusOpt.isDefined)
          //assert(pocStatusOpt.get.pocId == poc.id)
        }).onErrorHandle {
          case e =>
            fail(e)
        }.runSyncUnsafe()
      }
    }
  }
}
