package com.ubirch.services.poc.parsers

import cats.data.Validated.{ Invalid, Valid }
import cats.syntax.apply._
import com.ubirch.PocConfig
import com.ubirch.models.csv.PocAdminRow
import com.ubirch.models.poc
import com.ubirch.models.poc._
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.util.CsvConstants
import com.ubirch.services.poc.util.CsvConstants._
import com.ubirch.services.util.Validator._

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.util.{ Failure, Success }

case class PocAdminParseResult(poc: Poc, pocAdmin: PocAdmin, csvRow: String) extends ParseRowResult

class PocAdminCsvParser(pocConfig: PocConfig) extends CsvParser[PocAdminParseResult] {
  protected def parseRow(cols: Array[String], line: String, tenant: Tenant): Either[String, PocAdminParseResult] = {
    PocAdminRow.fromCsv(cols) match {
      case Success(csvPocAdmin) =>
        val pocAddress = validatePocAddress(csvPocAdmin)
        val pocManager = validatePocManager(csvPocAdmin)
        val pocV = validatePoc(csvPocAdmin, pocAddress, pocManager, tenant)
        val pocAndAdminV = validatePocAdminInfo(csvPocAdmin, pocV)

        pocAndAdminV match {
          case Valid((poc, pocAdmin)) =>
            Right(PocAdminParseResult(poc, pocAdmin, line))
          case Invalid(errors) =>
            Left(line + columnSeparator + errors.toList.mkString(comma))
        }
      case Failure(_) =>
        Left(line + columnSeparator + s"the number of columns ${cols.length} is invalid. should be $pocAdminHeaderColOrderLength.")
    }
  }

  val headerColOrder: Array[String] = pocAdminHeaderColsOrder

  private def validatePocAdminInfo(
    csvPocAdmin: PocAdminRow,
    pocV: AllErrorsOr[Poc]
  ): AllErrorsOr[(Poc, PocAdmin)] = {
    (
      validateStringCSV(technicianName, csvPocAdmin.adminName),
      validateStringCSV(technicianSurname, csvPocAdmin.adminSurname),
      validateEmailFromCSV(technicianEmail, csvPocAdmin.adminEmail),
      validatePhoneFromCSV(technicianMobilePhone, csvPocAdmin.adminMobilePhone),
      validateDateCSV(technicianDateOfBirth, csvPocAdmin.adminDateOfBirth),
      validateBoolean(webIdentRequired, csvPocAdmin.webIdentRequired),
      pocV
    ).mapN {
      (
        adminName,
        adminSurname,
        adminEmail,
        adminMobilePhone,
        adminDateOfBirth,
        webIdentRequired,
        poc
      ) =>
        {
          val uuid = UUID.randomUUID()
          val pocAdmin = PocAdmin(
            uuid,
            poc.id,
            poc.tenantId,
            adminName,
            adminSurname,
            adminEmail,
            adminMobilePhone,
            webIdentRequired,
            adminDateOfBirth
          )
          (poc, pocAdmin)
        }
    }
  }

  private def validatePoc(
    csvPocAdmin: PocAdminRow,
    pocAddress: AllErrorsOr[Address],
    pocManager: AllErrorsOr[PocManager],
    tenant: Tenant): AllErrorsOr[Poc] =
    (
      validateExternalId(externalId, csvPocAdmin.externalId, tenant),
      validatePocType(pocType, csvPocAdmin.pocType, pocConfig.pocTypeEndpointMap, tenant),
      validatePocName(pocName, csvPocAdmin.pocName),
      pocAddress,
      validatePhoneFromCSV(phone, csvPocAdmin.pocPhone),
      validateLogoURL(logoUrl, csvPocAdmin.logoUrl, csvPocAdmin.pocType.endsWith("_app").toString),
      validateJson(jsonConfig, csvPocAdmin.extraConfig, tenant),
      pocManager
    ).mapN {
      (
        externalId,
        pocType,
        pocName,
        address,
        pocPhone,
        logoUrl,
        extraConfig,
        manager) =>
        {
          poc.Poc(
            UUID.randomUUID(),
            tenant.id,
            externalId,
            pocType,
            new String(pocName.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
            address,
            pocPhone,
            logoUrl.map(LogoURL(_)),
            extraConfig.map(JsonConfig(_)),
            manager,
            status = Pending
          )
        }
    }

  private def validatePocManager(csvPocAdmin: PocAdminRow): AllErrorsOr[PocManager] =
    (
      validateStringCSV(managerSurname, csvPocAdmin.managerSurname),
      validateStringCSV(managerName, csvPocAdmin.managerName),
      validateEmailFromCSV(managerEmail, csvPocAdmin.managerEmail),
      validatePhoneFromCSV(managerMobilePhone, csvPocAdmin.managerMobilePhone)
    ).mapN { (managerSurname, managerName, managerEmail, managerMobilePhone) =>
      PocManager(
        managerSurname,
        managerName,
        managerEmail,
        managerMobilePhone
      )
    }

  private def validatePocAddress(csvPocAdmin: PocAdminRow): AllErrorsOr[Address] =
    (
      validateStringCSV(street, csvPocAdmin.pocStreet),
      validateStringCSV(streetNumber, csvPocAdmin.pocHouseNumber),
      validateStringOption(csvPocAdmin.pocAdditionalAddress),
      validateZipCode(CsvConstants.zipcode, csvPocAdmin.pocZipcode),
      validateStringCSV(CsvConstants.city, csvPocAdmin.pocCity),
      validateStringOption(csvPocAdmin.pocCounty),
      validateStringOption(csvPocAdmin.pocFederalState),
      validateStringCSV(CsvConstants.country, csvPocAdmin.pocCountry)
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
