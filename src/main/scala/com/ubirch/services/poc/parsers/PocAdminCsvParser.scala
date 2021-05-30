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
      validateString(technicianName, csvPocAdmin.adminName),
      validateString(technicianSurname, csvPocAdmin.adminSurname),
      validateEmail(technicianEmail, csvPocAdmin.adminEmail),
      validatePhone(technicianMobilePhone, csvPocAdmin.adminMobilePhone),
      validateDate(technicianDateOfBirth, csvPocAdmin.adminDateOfBirth),
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
      validateString(externalId, csvPocAdmin.externalId),
      validateMapContainsStringKey(pocType, csvPocAdmin.pocType, pocConfig.pocTypeEndpointMap),
      validateString(pocName, csvPocAdmin.pocName),
      pocAddress,
      validatePhone(phone, csvPocAdmin.pocPhone),
      validateAdminCertifyApp(certifyApp, csvPocAdmin.pocCertifyApp),
      validateLogoURL(logoUrl, csvPocAdmin.logoUrl, csvPocAdmin.pocCertifyApp),
      validateClientCertAdmin(clientCert, csvPocAdmin.clientCert),
      validateJson(jsonConfig, csvPocAdmin.extraConfig),
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
            extraConfig.map(JsonConfig(_)),
            manager,
            status = Pending
          )
        }
    }

  private def validatePocManager(csvPocAdmin: PocAdminRow): AllErrorsOr[PocManager] =
    (
      validateString(managerSurname, csvPocAdmin.managerSurname),
      validateString(managerName, csvPocAdmin.managerName),
      validateEmail(managerEmail, csvPocAdmin.managerEmail),
      validatePhone(managerMobilePhone, csvPocAdmin.managerMobilePhone)
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
      validateString(street, csvPocAdmin.pocStreet),
      validateString(streetNumber, csvPocAdmin.pocHouseNumber),
      validateStringOption(csvPocAdmin.pocAdditionalAddress),
      validateZipCode(CsvConstants.zipcode, csvPocAdmin.pocZipcode),
      validateString(CsvConstants.city, csvPocAdmin.pocCity),
      validateStringOption(csvPocAdmin.pocCounty),
      validateStringOption(csvPocAdmin.pocFederalState),
      validateString(CsvConstants.country, csvPocAdmin.pocCountry)
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
