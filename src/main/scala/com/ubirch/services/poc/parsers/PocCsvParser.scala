package com.ubirch.services.poc.parsers

import cats.data.Validated.{ Invalid, Valid }
import cats.implicits.{ catsSyntaxTuple11Semigroupal, catsSyntaxTuple4Semigroupal, catsSyntaxTuple8Semigroupal }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
import com.ubirch.models.csv.PocRow
import com.ubirch.models.poc
import com.ubirch.models.poc._
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.CsvConstants
import com.ubirch.services.poc.util.CsvConstants._
import com.ubirch.services.util.Validator._

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.util.{ Failure, Success }

case class PocParseResult(poc: Poc, csvRow: String) extends ParseRowResult

class PocCsvParser(pocConfig: PocConfig) extends CsvParser[PocParseResult] with LazyLogging {
  protected def parseRow(cols: Array[String], line: String, tenant: Tenant): Either[String, PocParseResult] = {
    PocRow.fromCsv(cols) match {
      case Success(csvPoc) =>
        val pocAddress = validatePocAddress(csvPoc)
        val pocManager = validatePocManager(csvPoc)
        val poc = validatePoc(csvPoc, pocAddress, pocManager, tenant)

        poc match {
          case Valid(poc) =>
            Right(PocParseResult(poc, line))
          case Invalid(errors) =>
            Left(line + columnSeparator + errors.toList.mkString(comma))
        }
      case Failure(_) =>
        Left(line + columnSeparator +
          s"the number of column ${cols.length} is invalid. should be ${pocHeaderColsOrder.length}.")
    }
  }

  val headerColOrder: Array[String] = pocHeaderColsOrder

  private def validatePoc(
    csvPoc: PocRow,
    pocAddress: AllErrorsOr[Address],
    pocManager: AllErrorsOr[PocManager],
    tenant: Tenant): AllErrorsOr[Poc] =
    (
      validateString(externalId, csvPoc.externalId),
      validateMapContainsStringKey(pocType, csvPoc.pocType, pocConfig.pocTypeEndpointMap),
      validatePocName(pocName, csvPoc.pocName),
      pocAddress,
      validatePhone(phone, csvPoc.pocPhone),
      validateBoolean(certifyApp, csvPoc.pocCertifyApp),
      validateLogoURL(logoUrl, csvPoc.logoUrl, csvPoc.pocCertifyApp),
      validateClientCert(clientCert, csvPoc.clientCert, tenant),
      validateMapContainsStringKey(dataSchemaId, csvPoc.dataSchemaId, pocConfig.dataSchemaGroupMap),
      validateJson(jsonConfig, csvPoc.extraConfig),
      pocManager
    ).mapN {
      (
        externalId,
        pocType,
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
            pocType,
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
