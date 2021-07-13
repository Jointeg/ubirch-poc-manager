package com.ubirch.models.csv

import scala.util.Try

case class PocAdminRow(
  externalId: String,
  pocType: String,
  pocName: String,
  pocStreet: String,
  pocHouseNumber: String,
  pocAdditionalAddress: String,
  pocZipcode: String,
  pocCity: String,
  pocCounty: String,
  pocFederalState: String,
  pocCountry: String,
  pocPhone: String,
  logoUrl: String,
  managerSurname: String,
  managerName: String,
  managerEmail: String,
  managerMobilePhone: String,
  extraConfig: String,
  adminSurname: String,
  adminName: String,
  adminEmail: String,
  adminMobilePhone: String,
  adminDateOfBirth: String,
  webIdentRequired: String)

object PocAdminRow {

  def fromCsv(columns: Array[String]): Try[PocAdminRow] = Try {
    PocAdminRow(
      externalId = columns(0).trim,
      pocType = columns(1).trim,
      pocName = columns(2).trim,
      pocStreet = columns(3).trim,
      pocHouseNumber = columns(4).trim,
      pocAdditionalAddress = columns(5).trim,
      pocZipcode = columns(6).trim,
      pocCity = columns(7).trim,
      pocCounty = columns(8).trim,
      pocFederalState = columns(9).trim,
      pocCountry = columns(10).trim,
      pocPhone = columns(11).trim,
      logoUrl = columns(12).trim,
      managerSurname = columns(13).trim,
      managerName = columns(14).trim,
      managerEmail = columns(15).trim.toLowerCase,
      managerMobilePhone = columns(16).trim,
      extraConfig = columns(17).trim,
      adminSurname = columns(18).trim,
      adminName = columns(19).trim,
      adminEmail = columns(20).trim.toLowerCase,
      adminMobilePhone = columns(21).trim,
      adminDateOfBirth = columns(22).trim,
      webIdentRequired = columns(23).trim
    )
  }
}
