package com.ubirch.testutils

import com.ubirch.ModelCreationHelper.{ pocBmgTypeValue, pocTypeValue }
import com.ubirch.services.poc.util.CsvConstants.{
  columnSeparator,
  pocAdminHeaderLine,
  pocHeaderColsOrder,
  pocHeaderLine
}

import java.util.UUID

object CentralCsvProvider {

  def toShortHeaderCsv(pocId: UUID): String =
    s"""${pocHeaderColsOrder.drop(2).mkString(columnSeparator)}
       |${pocId.toString};$pocTypeValue;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+4974339296;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  def validPocOnlyCsv(pocId: UUID): String =
    s"""$pocHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+4974339296;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  def validHeaderButBadRowsPocOnlyCsv(pocId: UUID): String =
    s"""$pocHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+4974339296;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;;Musterfrau;Frau;frau.musterfraumail.de;+4974339296;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;+4974339296;;Musterfrau;Frau;frau.musterfrau@mail.de;+4974339296;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;;certification;Musterfrau;Frau;frau.musterfrau@mail.de;+4974339296;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;;Musterfrau;Frau;frau.musterfrau@mail.de;+4974339296;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;;Musterfrau;Frau;frau.musterfrau@mail.de;+4974339296;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  // header has wrong names
  val invalidHeaderPocOnlyCsv: String =
    s"""poc_id*;poc_type*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;client_cert;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      s"a5a62b0f-6694-4916-b188-89e69264458f;$pocTypeValue;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;TRUE;;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]}""".stripMargin

  val notEnoughHeaderPocOnlyCsv: String =
    s"""poc_id*;poc_type*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;encoding*;extra_signing_key_id;manager_surname*;manager_name*;manager_email*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;$pocTypeValue;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;TRUE;;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;{\"vaccines\":[\"vaccine1: vaccine2\"]}""".stripMargin

  def validHeaderButNotEnoughRowsPocOnlyCsv(pocId: UUID): String =
    s"""$pocHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+4974339296;;Musterfrau;Frau;frau.musterfrau@mail.de;{"vaccines":["vaccine1", "vaccine2"]}
       |""".stripMargin

  def invalidHeaderPocAdminCsv =
    s"""external_id*;poc_type_invalid*;poc_name*;street*;street_number*;additional_address;zipcode*;city*;county;federal_state;country*;phone*;certify_app*;logo_url;client_cert*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config;technician_surname;technician_name*;technician_email*;technician_mobile_phone*;technician_date_of_birth*;web_ident_required*\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;$pocTypeValue;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]};Mustermann;Herr;herr.mustermann@mail.de;0176-543;01.01.1971;TRUE"""

  def validHeaderButBadRowsPocAdminCsvForBmg(pocId: UUID): String =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocBmgTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"sealId":"cbd21cc8-e0fb-498e-8a85-ee622063a847"};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;TRUE
       |123454ab;$pocBmgTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"sealId":"cbd21cc8-e0fb-498e-8a85-ee622063a847"};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;TRUE
       |123456789;$pocBmgTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"sealId":"cbd21cc8-e0fb-498e-8a85-ee622063a847"};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;TRUE
       |123ABD;$pocBmgTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"sealId":"cbd21cc8-e0fb-498e-8a85-ee622063a847"};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;TRUE
       |""".stripMargin

  def validPocAdminCsv(pocId: UUID): String =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;TRUE
       |""".stripMargin

  def validHeaderButBadRowsPocAdminCsv(pocId: UUID): String =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;TRUE
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;01.1971;TRUE
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;01.01.1971
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;+591 74339296;http://www.ubirch.com/logo.png;Musterfrau;Frau;frau.musterfrau@mail.de;+591 74339296;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;+591 74339296;01.01.1971;01.01.1971;TRUE
       |""".stripMargin

}
