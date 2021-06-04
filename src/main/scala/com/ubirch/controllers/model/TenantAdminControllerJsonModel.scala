package com.ubirch.controllers.model

import com.ubirch.models.poc._
import org.joda.time.LocalDate

import java.util.UUID

object TenantAdminControllerJsonModel {
  case class PocAdmin_OUT(
    id: UUID,
    firstName: String,
    lastName: String,
    dateOfBirth: LocalDate,
    email: String,
    phone: String,
    pocName: String,
    active: Boolean,
    state: Status,
    webIdentInitiateId: Option[UUID],
    webIdentSuccessId: Option[String]
  )

  case class PocAdmin_IN(
    firstName: String,
    lastName: String,
    dateOfBirth: LocalDate,
    email: String,
    phone: String
  ) {
    def copyToPocAdmin(pa: PocAdmin): PocAdmin =
      pa.copy(
        name = firstName,
        surname = lastName,
        email = email,
        dateOfBirth = BirthDate(dateOfBirth),
        mobilePhone = phone
      )
  }

  case class Poc_IN(
    address: Address_IN,
    phone: String,
    manager: PocManager_IN
  ) {
    def copyToPoc(poc: Poc): Poc =
      poc.copy(
        phone = phone,
        address = address.toAddress,
        manager = manager.toManager
      )
  }

  case class Address_IN(
    street: String,
    houseNumber: String,
    additionalAddress: Option[String] = None,
    zipcode: Int,
    city: String,
    county: Option[String] = None,
    federalState: Option[String],
    country: String
  ) {
    def toAddress: Address =
      Address(
        street = street,
        houseNumber = houseNumber,
        additionalAddress = additionalAddress,
        zipcode = zipcode,
        city = city,
        county = county,
        federalState = federalState,
        country = country
      )
  }

  case class PocManager_IN(
    lastName: String,
    firstName: String,
    email: String,
    mobilePhone: String
  ) {
    def toManager: PocManager =
      PocManager(
        managerSurname = lastName,
        managerName = firstName,
        managerEmail = email,
        managerMobilePhone = mobilePhone
      )
  }

  object PocAdmin_OUT {
    def fromPocAdmin(pocAdmin: PocAdmin, poc: Poc): PocAdmin_OUT =
      PocAdmin_OUT(
        id = pocAdmin.id,
        firstName = pocAdmin.name,
        lastName = pocAdmin.surname,
        dateOfBirth = pocAdmin.dateOfBirth.date,
        email = pocAdmin.email,
        phone = pocAdmin.mobilePhone,
        pocName = poc.pocName,
        active = pocAdmin.active,
        state = pocAdmin.status,
        webIdentInitiateId = pocAdmin.webIdentInitiateId,
        webIdentSuccessId = pocAdmin.webIdentId
      )
  }
}
