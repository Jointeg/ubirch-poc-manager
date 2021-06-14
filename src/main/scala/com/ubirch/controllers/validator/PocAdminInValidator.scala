package com.ubirch.controllers.validator

import cats.data.Validated._
import cats.data._
import cats.implicits._
import com.ubirch.controllers.model.TenantAdminControllerJsonModel.PocAdmin_IN
import com.ubirch.controllers.validator.PocAdminInValidator.PocAdminInResult
import com.ubirch.services.poc.util.ValidatorConstants

import javax.mail.internet.InternetAddress
import scala.util.{ Failure, Success, Try }

sealed trait PocAdminInValidator {
  protected def validateEmail(rawEmail: String): PocAdminInResult[String] =
    Try {
      val email = new InternetAddress(rawEmail)
      email.validate()
    } match {
      case Success(_) => rawEmail.validNec
      case Failure(_) => ("email" -> s"Invalid email address '$rawEmail'").invalidNec
    }

  protected def validatePhone(phone: String): PocAdminInResult[String] =
    phone match {
      case ValidatorConstants.phoneRegex(_*) => phone.validNec
      case _                                 => ("phone" -> s"Invalid phone number '$phone'").invalidNec
    }
}

object PocAdminInValidator extends PocAdminInValidator {
  type PocAdminInResult[A] = ValidatedNec[(String, String), A]

  def validate(in: PocAdmin_IN): PocAdminInResult[PocAdmin_IN] = {
    (validateEmail(in.email), validatePhone(in.phone)).mapN((_, _) => in)
  }
}
