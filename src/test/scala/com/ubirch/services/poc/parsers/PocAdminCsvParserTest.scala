package com.ubirch.services.poc.parsers

import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.services.poc.util.HeaderCsvException
import com.ubirch.testutils.CentralCsvProvider.{
  invalidHeaderPocAdminCsv,
  validHeaderButBadRowsPocAdminCsv,
  validPocAdminCsv
}
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
  private val tenant = createTenant()

  "PocAdminCsvParser" should {
    "parse a correct csv file correctly" in {
      val resultT = pocAdminCsvParser.parseList(validPocAdminCsv(pocId), tenant)
      val result = resultT.runSyncUnsafe()
      assert(result.size == 1)
      assert(result.head.isRight)
    }

    "throw a HeaderCsvException if header name is wrong" in {
      assertThrows[HeaderCsvException](pocAdminCsvParser.parseList(invalidHeaderPocAdminCsv, tenant).runSyncUnsafe())
    }

    "return invalid csvRows with errorMsg and validCsvRow as Poc" in {
      val resultT = pocAdminCsvParser.parseList(validHeaderButBadRowsPocAdminCsv(pocId), tenant)
      val result = resultT.runSyncUnsafe()
      assert(result.size == 4)
      assert(result.head.isRight)
      assert(result(
        1).left.get == s"""${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;TRUE;http://www.ubirch.com/logo.png;TRUE;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;01.1971;TRUE;column web_ident_required* must be either 'TRUE' or 'FALSE'""")
      assert(result(2).left.get ==
        s"""${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;TRUE;http://www.ubirch.com/logo.png;TRUE;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;01.01.1971;column web_ident_required* must be either 'TRUE' or 'FALSE'""")
      assert(result(3).left.get ==
        s"""${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;TRUE;http://www.ubirch.com/logo.png;TRUE;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;01.01.1971;TRUE;column web_ident_required* must be either 'TRUE' or 'FALSE'""")
    }
  }
}
