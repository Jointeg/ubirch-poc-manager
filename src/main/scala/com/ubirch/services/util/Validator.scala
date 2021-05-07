package com.ubirch.services.util

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.ValidatorConstants._
import org.json4s.JValue
import org.json4s.native.JsonMethods._

import java.net.URL
import javax.mail.internet.InternetAddress
import scala.util.{ Failure, Success, Try }

object Validator {

  type AllErrorsOr[A] = ValidatedNel[String, A]

  def validateJson(header: String, str: String): AllErrorsOr[Option[JValue]] = {
    if (str.isEmpty) None.validNel
    else
      Try(parse(str)) match {
        case Success(json) => Some(json).validNel
        case Failure(_)    => jsonError(header).invalidNel
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
  def validateURL(header: String, str: String, mandatory: String): AllErrorsOr[Option[URL]] = {
    Try(mandatory.toBoolean) match {
      case Success(boolean) if boolean =>
        Try(new URL(str)) match {
          case Success(url) => Some(url).validNel
          case Failure(_)   => urlError(header).invalidNel
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
  def validateClientCert(header: String, str: String, tenant: Tenant): AllErrorsOr[Boolean] = {
    validateBoolean(header, str) match {
      case Valid(false) if tenant.clientCert.isEmpty => clientCertError(header).invalidNel
      case validOrInvalid                            => validOrInvalid
    }
  }

  /**
    * exactly 1 number in this regex https://www.regextester.com/97440
    */
  def validatePhone(header: String, str: String): AllErrorsOr[String] = {
    val numbers = phoneRegex.findAllIn(str)
    if (numbers.size != 1)
      phoneValidationError(header).invalidNel
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

}
