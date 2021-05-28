package com.ubirch.services.poc.parsers

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.PocConfig
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar.mock
import com.ubirch.TestBase
import com.ubirch.services.poc.util.HeaderCsvException
import com.ubirch.testutils.CentralCsvProvider.{
  invalidHeaderPocOnlyCsv,
  notEnoughHeaderPocOnlyCsv,
  validHeaderButBadRowsPocOnlyCsv,
  validHeaderButNotEnoughRowsPocOnlyCsv,
  validPocOnlyCsv
}

import java.util.UUID

class PocCsvParserTest extends TestBase {

  private val pocConfigMock = mock[PocConfig]
  when(pocConfigMock.dataSchemaGroupMap).thenReturn(
    Map("dataSchemaGroups" -> "xxx", "certification-vaccination" -> "yyy")
  )
  when(pocConfigMock.pocTypeEndpointMap).thenReturn(
    Map("ub_vac_app" -> "xxx", "certification-vaccination" -> "yyy")
  )

  private val pocCsvParser = new PocCsvParser(pocConfigMock)
  private val pocId = UUID.randomUUID()

  private val tenant = createTenant()

  "PocCsvParser" should {
    "parse a correct csv file correctly" in {
      val resultT = pocCsvParser.parseList(validPocOnlyCsv(pocId), tenant)
      val result = resultT.runSyncUnsafe()
      assert(result.size == 1)
      assert(result.head.isRight)
    }

    "throw a HeaderCsvException if header name is wrong" in {
      assertThrows[HeaderCsvException](pocCsvParser.parseList(invalidHeaderPocOnlyCsv, tenant).runSyncUnsafe())
    }

    "throw a HeaderCsvException if header length is not enough" in {
      assertThrows[HeaderCsvException](pocCsvParser.parseList(notEnoughHeaderPocOnlyCsv, tenant).runSyncUnsafe())
    }

    "throw a HeaderCsvException if row length is not enough" in {
      val result = pocCsvParser.parseList(validHeaderButNotEnoughRowsPocOnlyCsv(pocId), tenant).runSyncUnsafe()
      assert(result.size == 1)
      assert(result.head.left.get == s"""${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;{"vaccines":["vaccine1", "vaccine2"]};the number of column 20 is invalid. should be 21.""")
    }

    "return invalid csvRows with errorMsg and validCsvRow as Poc" in {
      val resultT = pocCsvParser.parseList(validHeaderButBadRowsPocOnlyCsv(pocId), tenant)
      val result = resultT.runSyncUnsafe()
      assert(result.size == 6)
      assert(result.head.isRight)
      assert(result(
        1).left.get == s"""${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;FALSE;;Xfalse;certification-vaccination;Musterfrau;Frau;frau.musterfraumail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};column client_cert* must be either 'TRUE' or 'FALSE',column manager_email* must contain a proper mail address""")
      assert(result(
        2).left.get == s"""${pocId.toString};ub_vac_app;pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;0187-738786782;false;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};column street_number* cannot be empty,column zipcode* must have the length of 5 digits,column city* cannot be empty""")
      assert(result(
        3).left.get == s"""${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;false;;FALSE;certification;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};column data_schema_id* must contain a valid value from this map Map(dataSchemaGroups -> xxx, certification-vaccination -> yyy)""")
      assert(result(4).isRight)
      assert(result.last.left.get ==
        s"""${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;;false;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]};the number of column 20 is invalid. should be 21.""")
    }
  }
}
