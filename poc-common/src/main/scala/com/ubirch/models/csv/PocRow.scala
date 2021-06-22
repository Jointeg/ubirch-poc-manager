package com.ubirch.models.csv

import scala.util.Try

case class PocRow(
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
  extraConfig: String)

object PocRow {

  def fromCsv(columns: Array[String]): Try[PocRow] = Try {
    PocRow(
      externalId = columns(0),
      pocType = columns(1),
      pocName = columns(2),
      pocStreet = columns(3),
      pocHouseNumber = columns(4),
      pocAdditionalAddress = columns(5),
      pocZipcode = columns(6),
      pocCity = columns(7),
      pocCounty = columns(8),
      pocFederalState = columns(9),
      pocCountry = columns(10),
      pocPhone = columns(11),
      logoUrl = columns(12),
      managerSurname = columns(13),
      managerName = columns(14),
      managerEmail = columns(15),
      managerMobilePhone = columns(16),
      extraConfig = columns(17)
    )
  }
}
