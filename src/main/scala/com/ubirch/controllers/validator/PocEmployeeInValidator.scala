package com.ubirch.controllers.validator

import cats.data._
import cats.implicits._
import com.ubirch.controllers.model.PocAdminControllerJsonModel.PocEmployee_IN
import com.ubirch.controllers.validator.PocEmployeeInValidator.PocEmployeeInResult

import javax.mail.internet.InternetAddress
import scala.util.{ Failure, Success, Try }

sealed trait PocEmployeeInValidator {
  protected def validateEmail(rawEmail: String): PocEmployeeInResult[String] =
    Try {
      val email = new InternetAddress(rawEmail)
      email.validate()
    } match {
      case Success(_) => rawEmail.validNec
      case Failure(_) => ("email" -> s"Invalid email address '$rawEmail'").invalidNec
    }
}

object PocEmployeeInValidator extends PocEmployeeInValidator {
  type PocEmployeeInResult[A] = ValidatedNec[(String, String), A]

  def validate(in: PocEmployee_IN): PocEmployeeInResult[PocEmployee_IN] = validateEmail(in.email).map(_ => in)
}
