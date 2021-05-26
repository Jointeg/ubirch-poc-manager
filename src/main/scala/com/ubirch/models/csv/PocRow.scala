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
  pocCertifyApp: String,
  logoUrl: String,
  clientCert: String,
  managerSurname: String,
  managerName: String,
  managerEmail: String,
  managerMobilePhone: String,
  extraConfig: String)

object PocRow {

  def fromCsv(columns: Array[String]): Try[PocRow] = Try {
    PocRow(
      columns(0),
      columns(1),
      columns(2),
      columns(3),
      columns(4),
      columns(5),
      columns(6),
      columns(7),
      columns(8),
      columns(9),
      columns(10),
      columns(11),
      columns(12),
      columns(13),
      columns(14),
      columns(15),
      columns(16),
      columns(17),
      columns(18),
      columns(19)
    )
  }
}
