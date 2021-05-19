package com.ubirch.services.poc.util

object CsvConstants {

  val externalId = "external_id*"
  val pocType = "poc_type*"
  val pocName = "poc_name*"
  val street = "street*"
  val streetNumber = "street_number*"
  val additionalAddress = "additional_address"
  val zipcode = "zipcode*"
  val city = "city*"
  val county = "county"
  val federalState = "federal_state"
  val country = "country*"
  val phone = "phone*"
  val certifyApp = "certify_app*"
  val logoUrl = "logo_url"
  val clientCert = "client_cert*"
  val dataSchemaId = "data_schema_id*"
  val managerSurname = "manager_surname*"
  val managerName = "manager_name*"
  val managerEmail = "manager_email*"
  val managerMobilePhone = "manager_mobile_phone*"
  val jsonConfig = "extra_config"
  val technicianSurname = "technician_surname*"
  val technicianName = "technician_name*"
  val technicianEmail = "technician_email*"
  val technicianMobilePhone = "technician_mobile_phone*"
  val technicianDateOfBirth = "technician_date_of_birth*"
  val webIdentRequired = "web_ident_required*"

  val columnSeparator = ";"
  val comma = ","
  val carriageReturn = "\n"

  val pocHeaderColsOrder: Array[String] = Array(
    externalId,
    pocType,
    pocName,
    street,
    streetNumber,
    additionalAddress,
    zipcode,
    city,
    county,
    federalState,
    country,
    phone,
    certifyApp,
    logoUrl,
    clientCert,
    dataSchemaId,
    managerSurname,
    managerName,
    managerEmail,
    managerMobilePhone,
    jsonConfig
  )
  val pocHeaderColOrderLength: Int = pocHeaderColsOrder.length

  val pocAdminHeaderColsOrder: Array[String] = Array(
    externalId,
    pocType,
    pocName,
    street,
    streetNumber,
    additionalAddress,
    zipcode,
    city,
    county,
    federalState,
    country,
    phone,
    certifyApp,
    logoUrl,
    clientCert,
    dataSchemaId,
    managerSurname,
    managerName,
    managerEmail,
    managerMobilePhone,
    jsonConfig,
    technicianSurname,
    technicianName,
    technicianEmail,
    technicianMobilePhone,
    technicianDateOfBirth,
    webIdentRequired
  )
  val pocAdminHeaderColOrderLength: Int = pocAdminHeaderColsOrder.length

  val pocHeaderLine: String = pocHeaderColsOrder.mkString(columnSeparator)
  val pocAdminHeaderLine: String = pocAdminHeaderColsOrder.mkString(columnSeparator)

  def headerErrorMsg(col: String, header: String): String =
    s"$col didn't equal expected header $header; the right header order would be: ${pocHeaderColsOrder.mkString(comma)}"

}
