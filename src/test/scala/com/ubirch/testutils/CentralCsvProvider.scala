package com.ubirch.testutils

import com.ubirch.ModelCreationHelper.pocTypeValue
import com.ubirch.services.poc.util.CsvConstants.{ pocAdminHeaderLine, pocHeaderLine }

import java.util.UUID

object CentralCsvProvider {

  def validPocOnlyCsv(pocId: UUID): String =
    s"""$pocHeaderLine
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;http://www.ubirch.com/logo.png;False;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  def validHeaderButBadRowsPocOnlyCsv(pocId: UUID): String =
    s"""$pocHeaderLine
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;http://www.ubirch.com/logo.png;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;FALSE;;Xfalse;certification-vaccination;Musterfrau;Frau;frau.musterfraumail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;;;;;Wunschkreis;Wunschland;Deutschland;0187-738786782;false;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;false;;FALSE;certification;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;false;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;;false;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0187-738786782;{"vaccines":["vaccine1", "vaccine2"]}""".stripMargin

  // header has wrong names
  val invalidHeaderPocOnlyCsv: String =
    "poc_id*;poc_type*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;client_cert;data_schema_id*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;ub_vac_app;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]}"

  val notEnoughHeaderPocOnlyCsv: String =
    "poc_id*;poc_type*;poc_name*;poc_178street*;poc_house_number*;poc_additional_address;poc_zipcode*;poc_city*;poc_county;poc_federal_state;poc_country*;poc_phone*;certify_app*;logo_url;data_schema_id*;encoding*;extra_signing_key_id;manager_surname*;manager_name*;manager_email*;extra_config\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;ub_vac_app;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;{\"vaccines\":[\"vaccine1: vaccine2\"]}"

  def validHeaderButNotEnoughRowsPocOnlyCsv(pocId: UUID): String =
    s"""$pocHeaderLine
       |${pocId.toString};ub_vac_app;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;0187-738786782;TRUE;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;{"vaccines":["vaccine1", "vaccine2"]}
       |""".stripMargin

  def invalidHeaderPocAdminCsv =
    s"""external_id*;poc_type*;poc_name*;street*;street_number*;additional_address;zipcode*;city*;county;federal_state;country*;phone*;certify_app*;logo_url;client_cert*;data_schema_id*;manager_surname*;manager_name*;manager_email*;manager_mobile_phone*;extra_config;technician_surname;technician_name*;technician_email*;technician_mobile_phone*;technician_date_of_birth*;web_ident_required*\n" +
      "a5a62b0f-6694-4916-b188-89e69264458f;$pocTypeValue;Impfzentrum zum Löwen;An der Heide;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030-786862834;TRUE;;certification-vaccination;CBOR;Impfzentrum;Musterfrau;Frau;frau.musterfrau@mail.de;0176-543;{\"vaccines\":[\"vaccine1: vaccine2\"]};Mustermann;Herr;herr.mustermann@mail.de;0176-543;01.01.1971;TRUE"""

  def validPocAdminCsv(pocId: UUID): String =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;TRUE;http://www.ubirch.com/logo.png;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971;TRUE
       |""".stripMargin

  def validHeaderButBadRowsPocAdminCsv(pocId: UUID): String =
    s"""$pocAdminHeaderLine
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;false;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};;Herr;herr.mustermann@;0176-738786782;01.01.1971;xfalse
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;false;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;017782;01.1971;TRUE
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;false;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971
       |${pocId.toString};$pocTypeValue;pocName;pocStreet;101;;12636;Wunschstadt;Wunschkreis;Wunschland;Deutschland;030786862834;false;;FALSE;certification-vaccination;Musterfrau;Frau;frau.musterfrau@mail.de;0176-738786782;{"vaccines":["vaccine1: vaccine2"]};Mustermann;Herr;herr.mustermann@mail.de;0176-738786782;01.01.1971;TRUE
       |""".stripMargin

}
