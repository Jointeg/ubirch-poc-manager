package com.ubirch.services.poc

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ServicesConfPaths
import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.TestBase
import com.ubirch.services.poc.util.CsvConstants.headerLine
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock

import java.util.UUID

class CsvHandlerTest extends TestBase {

  private val configMock = mock[Config]
  when(configMock.getString(ServicesConfPaths.DATA_SCHEMA_GROUP_IDS)).thenReturn(
    "dataSchemaGroups,certification-vaccination")

  private val csvHandler = new CsvHandlerImp(configMock)
  private val pocId = UUID.randomUUID()

  private val invalidHeader =
    "poc_id*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;data_schema_id*;encoding*;extra_signing_key_id;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]}"

  private val notEnoughHeader =
    "poc_id*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;data_schema_id*;encoding*;extra_signing_key_id;manager_surname*;manager_name*;manager_email*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;{\"vaccines\":[\"vaccine1: vaccine2\"]}"

  private val validHeaderButNotEnoughRows =
    s"""$headerLine
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;{"vaccines":["vaccine1", "vaccine2"]}
       |""".stripMargin

  private val validCsv =
    s"""$headerLine
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val validHeaderButBadCsvRows =
    s"""$headerLine
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;Xfalse;certification-vaccination;Musterfrau;Frau;frau.musterfraumail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  private val tenant = createTenant()

  "CsvHandler" should {
    "parse a correct csv file correctly" in {
      val result = csvHandler.parsePocCreationList(validCsv, tenant)
      assert(result.size == 1)
      assert(result.head.isRight)
    }

    "throw a HeaderCsvException if header name is wrong" in {
      assertThrows[HeaderCsvException](csvHandler.parsePocCreationList(invalidHeader, tenant))
    }

    "throw a HeaderCsvException if header length is not enough" in {
      assertThrows[HeaderCsvException](csvHandler.parsePocCreationList(notEnoughHeader, tenant))
    }

    "throw a HeaderCsvException if row length is not enough" in {
      val result = csvHandler.parsePocCreationList(validHeaderButNotEnoughRows, tenant)
      assert(result.size == 1)
      assert(result.head.left.get == s"""${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;{"vaccines":["vaccine1", "vaccine2"]};the numbers of column 19 is invalid. should be 20.""")
    }

    "return invalid csvRows with errorMsg and validCsvRow as Poc" in {
      val result = csvHandler.parsePocCreationList(validHeaderButBadCsvRows, tenant)
      assert(result.size == 4)
      assert(result.head.isRight)
      assert(result(
        1).left.get == s"""${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;Xfalse;certification-vaccination;Musterfrau;Frau;frau.musterfraumail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};column client_cert* must be either 'TRUE' or 'FALSE',column manager_email* must contain a proper mail address""")
      assert(result(
        2).left.get == s"""${pocId.toString};pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};column street_number* cannot be empty,column zipcode* must have the length of 5 digits,column city* cannot be empty""")
      assert(result.last.isRight)
    }
  }

}
