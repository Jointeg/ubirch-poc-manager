package com.ubirch.services.poc.parsers

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.PocConfig
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock
import com.ubirch.TestBase
import com.ubirch.services.poc.util.CsvConstants.pocHeaderLine
import com.ubirch.services.poc.util.HeaderCsvException

import java.util.UUID

class PocCsvParserTest extends TestBase {

  private val pocConfigMock = mock[PocConfig]
  when(pocConfigMock.pocTypeDataSchemaMap).thenReturn(
    Map("ub_vac_app" -> "xxx", "certification-vaccination" -> "yyy")
  )
  when(pocConfigMock.pocTypeEndpointMap).thenReturn(
    Map("ub_vac_app" -> "xxx", "certification-vaccination" -> "yyy")
  )

  private val pocCsvParser = new PocCsvParser(pocConfigMock)
  private val pocId = UUID.randomUUID()

  // header has wrong names
  private val invalidHeader =
    "poc_id*;poc_type*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;client_cert;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;ub_vac_app;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]}"

  private val notEnoughHeader =
    "poc_id*;poc_type*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;encoding*;extra_signing_key_id;manager_surname*;manager_name*;manager_email*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;ub_vac_app;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;{\"vaccines\":[\"vaccine1: vaccine2\"]}"

  private val validHeaderButNotEnoughRows =
    s"""$pocHeaderLine
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;{"vaccines":["vaccine1", "vaccine2"]}
       |""".stripMargin

  private val validCsv =
    s"""$pocHeaderLine
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val validHeaderButBadCsvRows =
    s"""$pocHeaderLine
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;Xfalse;Musterfrau;Frau;frau.musterfraumail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_ap;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val tenant = createTenant()

  "PocCsvParser" should {
    "parse a correct csv file correctly" in {
      val resultT = pocCsvParser.parseList(validCsv, tenant)
      val result = resultT.runSyncUnsafe()
      assert(result.size == 1)
      assert(result.head.isRight)
    }

    "throw a HeaderCsvException if header name is wrong" in {
      assertThrows[HeaderCsvException](pocCsvParser.parseList(invalidHeader, tenant).runSyncUnsafe())
    }

    "throw a HeaderCsvException if header length is not enough" in {
      assertThrows[HeaderCsvException](pocCsvParser.parseList(notEnoughHeader, tenant).runSyncUnsafe())
    }

    "throw a HeaderCsvException if row length is not enough" in {
      val result = pocCsvParser.parseList(validHeaderButNotEnoughRows, tenant).runSyncUnsafe()
      assert(result.size == 1)
      assert(result.head.left.get == s"""${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;{"vaccines":["vaccine1", "vaccine2"]};the number of column 19 is invalid. should be 20.""")
    }

    "return invalid csvRows with errorMsg and validCsvRow as Poc" in {
      val resultT = pocCsvParser.parseList(validHeaderButBadCsvRows, tenant)
      val result = resultT.runSyncUnsafe()
      assert(result.size == 6)
      assert(result.head.isRight)
      assert(result(
        1).left.get == s"""${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;Xfalse;Musterfrau;Frau;frau.musterfraumail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};column client_cert* must be either 'TRUE' or 'FALSE',column manager_email* must contain a proper mail address""")
      assert(result(
        2).left.get == s"""${pocId.toString};ub_vac_app;pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};column street_number* cannot be empty,column zipcode* must have the length of 5 digits,column city* cannot be empty""")
      assert(result(
        3).left.get == s"""${pocId.toString};ub_vac_ap;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};column poc_type* must contain a valid value from this map Map(ub_vac_app -> xxx, certification-vaccination -> yyy)""")
      assert(result(4).isRight)
      assert(result.last.left.get ==
        s"""${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;;FALSE;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};the number of column 19 is invalid. should be 20.""")
    }
  }
}
