package com.ubirch.services.util

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import com.ubirch.models.poc.PocLogo
import com.ubirch.models.tenant._
import com.ubirch.services.poc.util.ValidatorConstants._
import org.joda.time.LocalDate
import org.joda.time.format.{ DateTimeFormat, DateTimeFormatter }
import org.json4s.ext.JavaTypesSerializers
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.{ DefaultFormats, Formats, JValue }

import java.net.URL
import java.util.UUID
import javax.mail.internet.InternetAddress
import scala.util.{ Failure, Success, Try }

case class SealJson(sealId: UUID)

object Validator {

  implicit val formats: Formats = DefaultFormats.lossless ++ JavaTypesSerializers.all
  type AllErrorsOr[A] = ValidatedNel[String, A]

  def validateJson(header: String, str: String, tenant: Tenant): AllErrorsOr[Option[JValue]] = {
    if (str.isEmpty && tenant.tenantType != BMG) None.validNel
    else if (str.isEmpty) jsonErrorWhenTenantTypeBMG(header).invalidNel
    else
      (tenant.tenantType, Try(parse(str))) match {
        case (BMG, Success(jValue)) => validateJsonWithSealId(header, str, jValue)
        case (BMG, Failure(_))      => jsonErrorWhenTenantTypeBMG(header).invalidNel
        case (_, Failure(_))        => jsonError(header).invalidNel
        case (_, Success(jValue))   => Some(jValue).validNel
      }

  }

  private def validateJsonWithSealId(header: String, str: String, jValue: JValue): AllErrorsOr[Option[JValue]] = {
    Try(Serialization.read[SealJson](str)) match {
      case Success(_) => Some(jValue).validNel
      case Failure(_) => jsonErrorWhenTenantTypeBMG(header).invalidNel
    }
  }

  /**
    * parsable to Email
    */
  def validateEmail(header: String, str: String): AllErrorsOr[String] = {
    Try {
      val email = new InternetAddress(str)
      email.validate()
    } match {
      case Success(_) => str.validNel
      case Failure(_) => emailError(header).invalidNel
    }
  }

  /**
    * parsable to URL
    */
  def validateLogoURL(header: String, str: String, mandatory: String): AllErrorsOr[Option[URL]] = {
    Try(mandatory.toBoolean) match {
      case Success(boolean) if boolean =>
        Try(new URL(str)) match {
          case Success(url) =>
            if (PocLogo.hasAcceptedFileExtension(url)) Some(url).validNel
            else logoUrlNoValidFileFormatError(header).invalidNel
          case Failure(_) => logoUrlNoValidUrlError(header).invalidNel
        }
      case _ => None.validNel
    }
  }

  /**
    * parsable to Boolean
    */
  def validateBoolean(header: String, str: String): AllErrorsOr[Boolean] = {
    Try(str.toBoolean) match {
      case Success(boolean) => boolean.validNel
      case Failure(_)       => booleanError(header).invalidNel
    }
  }

  /**
    * parsable to Boolean
    */
  def validateAdminCertifyApp(header: String, str: String): AllErrorsOr[Boolean] = {
    Try(str.toBoolean) match {
      case Success(true)  => true.validNel
      case Success(false) => certifyAppAdminError(header).invalidNel
      case Failure(_)     => booleanError(header).invalidNel
    }
  }

  /**
    * parsable to Boolean
    */
  def validateClientCertAdmin(header: String, str: String): AllErrorsOr[Boolean] = {
    validateBoolean(header, str) match {
      case Valid(false)   => clientCertAdminError(header).invalidNel
      case validOrInvalid => validOrInvalid
    }
  }

  /**
    * parsable to Boolean
    */
  def validateClientCert(header: String, str: String, tenant: Tenant): AllErrorsOr[Boolean] = {
    validateBoolean(header, str) match {
      case Valid(false) if tenant.sharedAuthCert.isEmpty => clientCertError(header).invalidNel
      case Valid(false) if tenant.usageType == APP =>
        organisationalUnitCertError(APP, clientCertRequired = false).invalidNel
      case Valid(true) if tenant.usageType == API =>
        organisationalUnitCertError(API, clientCertRequired = true).invalidNel
      case validOrInvalid => validOrInvalid
    }
  }

  /**
    * exactly 1 number in this regex https://www.regextester.com/97440
    */
  def validatePhone(header: String, str: String): AllErrorsOr[String] = {
    str match {
      case phoneRegex(_*) => str.validNel
      case _              => phoneValidationError(header).invalidNel
    }
  }

  /**
    * string not empty
    */
  def validateString(header: String, str: String): AllErrorsOr[String] = {
    if (str == "")
      emptyStringError(header).invalidNel
    else
      str.validNel
  }

  def validateStringWithRange(header: String, str: String, min: Int, max: Int): AllErrorsOr[String] = {
    if (str.length < min || str.length > max)
      rangeOverStringError(header, min, max).invalidNel
    else
      str.validNel
  }

  /**
    * string key exists in map
    */
  def validatePocType(
    header: String,
    pocType: String,
    map: Map[String, String],
    tenant: Tenant): AllErrorsOr[String] = {
    if (map.contains(pocType)) {
      tenant.tenantType match {
        case UBIRCH if pocType.contains("ub") => pocType.validNel
        case BMG if pocType.contains("bmg")   => pocType.validNel
        case _                                => pocTypeMustCorrelateWithTenantType(header, tenant.tenantType).invalidNel
      }
    } else
      mapDoesntContainStringKeyError(header, map).invalidNel
  }

  /**
    * for bmg, it must follow the bmgExternalIdRegex pattern
    */
  def validateExternalId(header: String, externalId: String, tenant: Tenant): AllErrorsOr[String] = {
    tenant.tenantType match {
      case UBIRCH => validateString(header, externalId)
      case BMG =>
        externalId match {
          case bmgExternalIdRegex(_*) => externalId.validNel
          case _                      => bmgExternalIdValidationError(header).invalidNel
        }
    }
  }

  /**
    * None if string empty
    */
  def validateStringOption(str: String): AllErrorsOr[Option[String]] = {
    if (str == "") None.validNel else Some(str).validNel
  }

  /**
    * length of zipcode is 5 and only contains numbers
    * only valid for German zip codes at the moment
    */
  def validateZipCode(header: String, str: String): AllErrorsOr[Int] = {
    if (str.isEmpty || str.length != 5)
      zipCodeLengthError(header).invalidNel
    else
      Try(str.toInt) match {
        case Success(int) => int.validNel
        case Failure(_)   => zipCodeDigitError(header).invalidNel
      }
  }

  def validatePocName(header: String, pocName: String): AllErrorsOr[String] = {
    if (pocName.trim.length < 4) tooShortStringError(header, 4).invalidNel
    else {
      val matches = pocNameRegex.findAllIn(pocName).toSeq
      if (matches.nonEmpty) pocName.validNel
      else pocNameValidationError(header).invalidNel
    }
  }

  /**
    * Valid Date format
    */
  def validateDate(header: String, str: String): AllErrorsOr[LocalDate] = {
    Try(LocalDate.parse(str, `dd.MM.yyyy`)) match {
      case Success(date) => date.valid
      case Failure(_)    => birthOfDateError(header).invalidNel
    }
  }

  val `dd.MM.yyyy`: DateTimeFormatter = DateTimeFormat.forPattern("dd.MM.yyyy")
}
