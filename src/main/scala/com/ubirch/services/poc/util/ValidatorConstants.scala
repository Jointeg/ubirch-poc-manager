package com.ubirch.services.poc.util

import scala.util.matching.Regex

object ValidatorConstants {

  //    https://www.regextester.com/97440
  val phoneRegex: Regex =
    raw"(([+][(]?[0-9]{1,3}[)]?)|([(]?[0-9]{4}[)]?))\s*[)]?[-\s]?[(]?[0-9]{1,3}[)]?([-\s]?[0-9]{3})([-\s]?[0-9]{3,4})".r

  val pocNameRegex: Regex =
    "^(?:(?:\\p{Ll}|\\p{Lu}|\\p{Lo}|\\p{Lm})\\p{Mn}*|\\p{N})+([ _.@-](?:(?:\\p{Ll}|\\p{Lu}|\\p{Lo}|\\p{Lm})\\p{Mn}*|\\p{N})+)*$".r

  def pocNameValidationError(header: String) =
    s"column $header must contain a valid poc name"

  def phoneValidationError(header: String) =
    s"column $header must contain a valid phone number e.g. +46-498-313789"

  def booleanError(header: String) =
    s"column $header must be either 'TRUE' or 'FALSE'"

  def clientCertError(header: String) =
    s"column $header can only be false, if tenant has client cert"

  def urlError(header: String) =
    s"column $header must contain a proper url"

  def emailError(header: String) =
    s"column $header must contain a proper mail address"

  def jsonError(header: String) =
    s"column $header must contain a valid json string"

  def emptyStringError(header: String) =
    s"column $header cannot be empty"

  def zipCodeLengthError(header: String) =
    s"column $header must have the length of 5 digits"

  def zipCodeDigitError(header: String) =
    s"column $header must have the length of 5 digits"

}
