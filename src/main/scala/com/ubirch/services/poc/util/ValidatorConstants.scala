package com.ubirch.services.poc.util

import com.ubirch.models.tenant.{ TenantType, UsageType }

import scala.util.matching.Regex

object ValidatorConstants {

  //    https://www.regextester.com/97440
  val phoneRegex: Regex =
    "^(\\+|00)[0-9]{1,3}[ \\-0-9]{4,14}$".r

  val pocNameRegex: Regex =
    "^(?:(?:\\p{Ll}|\\p{Lu}|\\p{Lo}|\\p{Lm})\\p{Mn}*|\\p{N})+([ _.@-](?:(?:\\p{Ll}|\\p{Lu}|\\p{Lo}|\\p{Lm})\\p{Mn}*|\\p{N})+)*$".r

  def pocNameValidationError(header: String) =
    s"column $header must contain a valid poc name"

  def phoneValidationError(header: String) =
    s"column $header must contain a valid phone number e.g. +46-498-313789"

  def booleanError(header: String) =
    s"column $header must be either 'TRUE' or 'FALSE'"

  def certifyAppAdminError(header: String) =
    s"column $header cannot be false, if poc admin shall be created"

  def clientCertError(header: String) =
    s"column $header can only be false, if tenant has client cert"

  def clientCertAdminError(header: String) =
    s"column $header cannot be false, if poc admin shall be created"

  def organisationalUnitCertError(userType: UsageType, clientCertRequired: Boolean) =
    s"Could not create organisational unit because Tenant usage type is set to $userType but clientCertRequired is set to $clientCertRequired"

  def logoUrlNoValidUrlError(header: String) =
    s"column $header must contain a valid url http://www.ubirch.com if certifyApp is set to true"

  def logoUrlNoValidFileFormatError(header: String) =
    s"column $header must contain a valid file extension (jpg, jpeg, png)"

  def emailError(header: String) =
    s"column $header must contain a proper mail address"

  def jsonError(header: String) =
    s"column $header must contain a valid json string"

  def emptyStringError(header: String) =
    s"column $header cannot be empty"

  def rangeOverStringError(header: String, minLength: Int, maxLength: Int) =
    s"column $header must be from $minLength to $maxLength"

  def tooShortStringError(header: String, minLength: Int) =
    s"column $header cannot be shorter than $minLength"

  def listDoesntContainStringError(header: String, list: Seq[String]) =
    s"column $header must contain a valid value from this list $list"

  def mapDoesntContainStringKeyError(header: String, map: Map[String, String]) =
    s"column $header must contain a valid value from this map $map"

  def pocTypeMustCorrelateWithTenantType(header: String, tenantType: TenantType) =
    s"column $header must contain a valid value matching the tenant type ${TenantType.toStringFormat(tenantType)}"

  def zipCodeLengthError(header: String) =
    s"column $header must have the length of 5 digits"

  def zipCodeDigitError(header: String) =
    s"column $header must have the length of 5 digits"

  def birthOfDateError(header: String) =
    s"column $header must contain a valid date e.g. 01.01.1970"

}
