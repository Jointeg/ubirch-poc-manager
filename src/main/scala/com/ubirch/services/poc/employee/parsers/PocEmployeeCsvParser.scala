package com.ubirch.services.poc.employee.parsers

import cats.data.Validated
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.csv.PocEmployeeRow
import com.ubirch.models.pocEmployee.PocEmployeeFromCsv
import com.ubirch.models.tenant.Tenant
import com.ubirch.models.user.{ Email, FirstName, LastName }
import com.ubirch.services.poc.parsers.{ CsvParser, ParseRowResult }
import com.ubirch.services.poc.util.CsvConstants._
import com.ubirch.services.util.Validator._

import scala.util.{ Failure, Success }

case class PocEmployeeCsvParseResult(pocEmployeeFromCsv: PocEmployeeFromCsv, csvRow: String) extends ParseRowResult

class PocEmployeeCsvParser extends CsvParser[PocEmployeeCsvParseResult] with LazyLogging {
  override protected def parseRow(
    cols: Array[String],
    line: String,
    tenant: Tenant): Either[String, PocEmployeeCsvParseResult] = {
    PocEmployeeRow.fromCsv(cols) match {
      case Success(csvPocEmployee) =>
        val validatedFirstName = validateString(firstName, csvPocEmployee.firstName).map(FirstName.apply)
        val validatedLastName = validateString(lastName, csvPocEmployee.lastName).map(LastName.apply)
        val validatedEmail = validateEmail(email, csvPocEmployee.email).map(Email.apply)

        validatePocEmployee(validatedFirstName, validatedLastName, validatedEmail) match {
          case Validated.Valid(pocEmployeeFromCsv) => Right(PocEmployeeCsvParseResult(pocEmployeeFromCsv, line))
          case Validated.Invalid(errors)           => Left(line + columnSeparator + errors.toList.mkString(comma))
        }
      case Failure(_) =>
        Left(line + columnSeparator + s"the number of column ${cols.length} is invalid. should be $pocEmployeeHeaderColsOrderLength.")
    }
  }

  override val headerColOrder: Array[String] = pocEmployeeHeaderColsOrder

  private def validatePocEmployee(
    validatedFirstName: AllErrorsOr[FirstName],
    validatedLastName: AllErrorsOr[LastName],
    validatedEmail: AllErrorsOr[Email]) = {
    (validatedFirstName, validatedLastName, validatedEmail).mapN(PocEmployeeFromCsv.apply)
  }
}
