package com.ubirch.services.poc

import com.ubirch.TestBase
import com.ubirch.services.poc.util.CsvConstants.headerLine

import java.util.UUID

class CsvHandlerTest extends TestBase {

  private val csvHandler = new CsvPocBatchParserImp
  private val pocId = UUID.randomUUID()

  private val badHeader =
    "poc_id*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;data_schema_id*;encoding*;extra_signing_key_id;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1; vaccine2\"]}"

  private val goodCsv =
    s"""$headerLine
       |${pocId.toString};pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  "CsvHandler" should {
    "parse a correct csv file correctly" in {
      val result = csvHandler.parsePocCreationList(goodCsv)
      assert(result.size == 1)
      assert(result.head.isRight)
    }

    "throw a HeaderCsvException if header name is wrong" in {
      assertThrows[HeaderCsvException](csvHandler.parsePocCreationList(badHeader))
    }
  }

}
