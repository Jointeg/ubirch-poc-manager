package com.ubirch.services.poc.parsers

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.services.poc.util.CsvConstants.pocAdminHeaderLine
import com.ubirch.services.poc.util.HeaderCsvException
import com.ubirch.{ PocConfig, TestBase }
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock

import java.util.UUID

class PocAdminCsvParserTest extends TestBase {
  private val pocConfigMock = mock[PocConfig]
  private val pocTypeValue = "ub_vac_app"
  when(pocConfigMock.dataSchemaGroupMap).thenReturn(
    Map("dataSchemaGroups" -> "xxx", "certification-vaccination" -> "yyy")
  )
  when(pocConfigMock.pocTypeEndpointMap).thenReturn(
    Map("ub_vac_app" -> "xxx", "certification-vaccination" -> "yyy")
  )

  private val pocAdminCsvParser = new PocAdminCsvParser(pocConfigMock)
  private val pocId = UUID.randomUUID()

  private val invalidHeader =
    s"""external_id;poc_type,poc_name;street;street_number;additional_address;zipcode;city;county;federal_state;country;phone;certify_app;logo_url;client_cert;data_schema_id;manager_surname;manager_name;manager_email;manager_mobile_phone;json_config;surname;technician_name;technician_email;technician_mobile_phone;technician_date_of_birth;web_ident_required\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;$pocTypeValue;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]};Mustermann;Herr;herr.mustermann@mail.de;0176-543;01.01.1971;TRUE"""

  private val validCsv =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971;TRUE
       |""".stripMargin

  private val validHeaderButBadCsvRows =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};;Herr;herr.mustermann@;0176-738786782;01.01.1971;xfalse
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;017782;01.1971;TRUE
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971;TRUE
       |""".stripMargin

  private val tenant = createTenant()

  "PocAdminCsvParser" should {
    "parse a correct csv file correctly" in {
      val resultT = pocAdminCsvParser.parseList(validCsv, tenant)
      val result = resultT.runSyncUnsafe()
      assert(result.size == 1)
      assert(result.head.isRight)
    }

    "throw a HeaderCsvException if header name is wrong" in {
      assertThrows[HeaderCsvException](pocAdminCsvParser.parseList(invalidHeader, tenant).runSyncUnsafe())
    }

    "return invalid csvRows with errorMsg and validCsvRow as Poc" in {
      val resultT = pocAdminCsvParser.parseList(validHeaderButBadCsvRows, tenant)
      val result = resultT.runSyncUnsafe()
      assert(result.size == 4)
      assert(result.head.left.get == s"""${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};;Herr;herr.mustermann@;0176-738786782;01.01.1971;xfalse;column technician_surname* cannot be empty,column technician_email* must contain a proper mail address,column web_ident_required* must be either 'TRUE' or 'FALSE'""")
      assert(result(
        1).left.get == s"""${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;017782;01.1971;TRUE;column technician_mobile_phone* must contain a valid phone number e.g. +46-498-313789,column technician_date_of_birth* must contain a valid date e.g. 01.01.1970""")
      assert(result(2).left.get ==
        s"""${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971;the number of columns 26 is invalid. should be 27.""")
      assert(result(3).isRight)
    }
  }
}
