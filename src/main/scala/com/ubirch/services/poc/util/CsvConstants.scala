package com.ubirch.services.poc.util

object CsvConstants {

  val externalId = "external_id*"
  val pocName = "poc_name*"
  val pocType = "poc_type*"
  val street = "street*"
  val streetNumber = "street_number*"
  val additionalAddress = "additional_address"
  val zipcode = "zipcode*"
  val city = "city*"
  val county = "county"
  val federalState = "federal_state"
  val country = "country*"
  val phone = "phone*"
  val logoUrl = "logo_url"
  val clientCert = "client_cert*"
  val managerSurname = "manager_surname*"
  val managerName = "manager_name*"
  val managerEmail = "manager_email*"
  val managerMobilePhone = "manager_mobile_phone*"
  val jsonConfig = "extra_config"

  val columnSeparator = ";"
  val comma = ","
  val carriageReturn = "\n"

  val headerColsOrder: Array[String] = Array(
    externalId,
    pocName,
    pocType,
    street,
    streetNumber,
    additionalAddress,
    zipcode,
    city,
    county,
    federalState,
    country,
    phone,
    logoUrl,
    clientCert,
    managerSurname,
    managerName,
    managerEmail,
    managerMobilePhone,
    jsonConfig
  )

  val headerLine: String = headerColsOrder.mkString(columnSeparator)

  def headerErrorMsg(col: String, header: String): String =
    s"$col didn't equal expected header $header; the right header order would be: ${headerColsOrder.mkString(comma)}"
}
