package com.ubirch.services.poc

import cats.data.Validated.{ Invalid, Valid }
import cats.implicits.{ catsSyntaxTuple10Semigroupal, catsSyntaxTuple4Semigroupal, catsSyntaxTuple8Semigroupal }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.csv.PocRow
import com.ubirch.models.poc
import com.ubirch.models.poc._
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.CsvConstants
import com.ubirch.services.poc.util.CsvConstants._
import com.ubirch.services.util.Validator._

import java.util.UUID
import scala.io.Source

trait CsvPocBatchParserTrait {

  @throws[HeaderCsvException]
  def parsePocCreationList(csv: String, tenant: Tenant): Seq[Either[String, (Poc, String)]]

}

sealed trait CsvException extends Exception

case class HeaderCsvException(errorMsg: String) extends CsvException
case class EmptyCsvException(errorMsg: String) extends CsvException

class CsvPocBatchParserImp extends CsvPocBatchParserTrait with LazyLogging {

  @throws[HeaderCsvException]
  override def parsePocCreationList(csv: String, tenant: Tenant): Seq[Either[String, (Poc, String)]] = {

    val s: Source = Source.fromString(csv)

    try {
      val lines = s.getLines()
      validateHeaders(lines)
      parseRows(tenant, lines)
    } catch {
      case ex: CsvException => throw ex
      case ex: Throwable =>
        val errorMsg = "something unexpected went wrong parsing the csv"
        logger.error(errorMsg, ex)
        Seq(Left(errorMsg))
    } finally {
      s.close()
    }
  }

  private def parseRows(tenant: Tenant, lines: Iterator[String]): Seq[Either[String, (Poc, String)]] = {
    lines.map { line =>
      val cols = line.split(columnSeparator).map(_.trim)
      parsePoC(cols, line, tenant)
    }.toSeq
  }

  @throws[CsvException]
  private def validateHeaders(lines: Iterator[String]): Unit = {
    if (lines.hasNext) {
      val cols = lines.next().split(columnSeparator).map(_.trim)
      headerColsOrder.zip(cols.toSeq).foreach {
        case (header, col) =>
          if (header != col)
            throw HeaderCsvException(headerErrorMsg(col, header))
      }
    } else {
      throw EmptyCsvException("the csv file mustn't be empty")
    }
  }

  private def parsePoC(cols: Array[String], line: String, tenant: Tenant): Either[String, (Poc, String)] = {

    val csvPoc = PocRow.fromCsv(cols)
    val pocAddress = validatePocAddress(csvPoc)
    val pocManager = validatePocManager(csvPoc)
    val poc = validatePoc(csvPoc, pocAddress, pocManager, tenant)

    poc match {
      case Valid(poc) =>
        Right(poc, line)
      case Invalid(errors) =>
        Left(line + columnSeparator + errors.toList.mkString(comma))
    }
  }

  private def validatePoc(
    csvPoc: PocRow,
    pocAddress: AllErrorsOr[Address],
    pocManager: AllErrorsOr[PocManager],
    tenant: Tenant): AllErrorsOr[Poc] =
    (
      validateString(externalId, csvPoc.externalId),
      validateString(pocName, csvPoc.pocName),
      pocAddress,
      validatePhone(phone, csvPoc.pocPhone),
      validateBoolean(certifyApp, csvPoc.pocCertifyApp),
      validateURL(logoUrl, csvPoc.logoUrl, csvPoc.logoUrl),
      validateClientCert(clientCert, csvPoc.clientCert, tenant),
      validateString(dataSchemaId, csvPoc.dataSchemaId),
      validateJson(jsonConfig, csvPoc.extraConfig),
      pocManager
    ).mapN {
      (
        externalId,
        pocName,
        address,
        pocPhone,
        pocCertifyApp,
        logoUrl,
        clientCert,
        dataSchemaId,
        extraConfig,
        manager) =>
        {
          poc.Poc(
            UUID.randomUUID(),
            tenant.id,
            externalId,
            pocName,
            address,
            pocPhone,
            pocCertifyApp,
            logoUrl.map(LogoURL(_)),
            clientCert,
            dataSchemaId,
            extraConfig.map(JsonConfig(_)),
            manager,
            status = Pending
          )
        }

    }

  private def validatePocManager(csvPoc: PocRow): AllErrorsOr[PocManager] =
    (
      validateString(managerSurname, csvPoc.managerSurname),
      validateString(managerName, csvPoc.managerName),
      validateEmail(managerEmail, csvPoc.managerEmail),
      validatePhone(managerMobilePhone, csvPoc.managerMobilePhone)
    ).mapN { (managerSurname, managerName, managerEmail, managerMobilePhone) =>
      PocManager(
        managerSurname,
        managerName,
        managerEmail,
        managerMobilePhone
      )
    }

  private def validatePocAddress(csvPoc: PocRow): AllErrorsOr[Address] =
    (
      validateString(street, csvPoc.pocStreet),
      validateString(streetNumber, csvPoc.pocHouseNumber),
      validateStringOption(csvPoc.pocAdditionalAddress),
      validateZipCode(CsvConstants.zipcode, csvPoc.pocZipcode),
      validateString(CsvConstants.city, csvPoc.pocCity),
      validateStringOption(csvPoc.pocCounty),
      validateStringOption(csvPoc.pocFederalState),
      validateString(CsvConstants.country, csvPoc.pocCountry)
    ).mapN { (pocStreet, pocHouseNumber, pocAddAddress, pocZipcode, pocCity, pocCounty, pocFederalState, pocCountry) =>
      Address(
        pocStreet,
        pocHouseNumber,
        pocAddAddress,
        pocZipcode,
        pocCity,
        pocCounty,
        pocFederalState,
        pocCountry
      )
    }

}
