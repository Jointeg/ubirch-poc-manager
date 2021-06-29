package com.ubirch.models.poc

import cats.syntax.apply._
import com.ubirch.models.tenant.TenantId
import com.ubirch.validator.Validator.{ validateEmail, validatePhone, validateString, AllErrorsOr }
import org.joda.time.{ DateTime, LocalDate }

import java.util.UUID

case class PocAdmin(
  id: UUID,
  pocId: UUID,
  tenantId: TenantId,
  name: String,
  surname: String,
  email: String,
  mobilePhone: String,
  webIdentRequired: Boolean,
  webIdentInitiateId: Option[UUID],
  webIdentId: Option[String],
  certifyUserId: Option[UUID],
  dateOfBirth: BirthDate,
  status: Status = Pending,
  active: Boolean = true,
  webAuthnDisconnected: Option[DateTime] = None,
  creationAttempts: Int = 0,
  lastUpdated: Updated = Updated(DateTime.now()),
  created: Created = Created(DateTime.now())
) extends HasCertifyUserId

object PocAdmin {

  def create(
    pocId: UUID,
    tenantId: TenantId,
    name: String,
    surName: String,
    email: String,
    mobilePhone: String,
    webIdentRequired: Boolean,
    dateOfBirth: LocalDate
  ): AllErrorsOr[PocAdmin] = {
    val emailV = validateEmail("email is invalid", email)
    val nameV = validateString("name is invalid", name)
    val surNameV = validateString("surName is invalid", surName)
    val mobilePhoneV = validatePhone("phone number is invalid", mobilePhone)
    (nameV, surNameV, emailV, mobilePhoneV).mapN { (name, surName, email, mobilePhone) =>
      val id = UUID.randomUUID()
      PocAdmin(
        id = id,
        pocId = pocId,
        tenantId = tenantId,
        name = name,
        surname = surName,
        email = email,
        mobilePhone = mobilePhone,
        webIdentRequired = webIdentRequired,
        webIdentInitiateId = None,
        webIdentId = None,
        certifyUserId = None,
        dateOfBirth = BirthDate(dateOfBirth)
      )
    }
  }

  def apply(
    id: UUID,
    pocId: UUID,
    tenantId: TenantId,
    name: String,
    surName: String,
    email: String,
    mobilePhone: String,
    webIdentRequired: Boolean,
    dateOfBirth: LocalDate): PocAdmin = {
    PocAdmin(
      id = id,
      pocId = pocId,
      tenantId = tenantId,
      name = name,
      surname = surName,
      email = email,
      mobilePhone = mobilePhone,
      webIdentRequired = webIdentRequired,
      webIdentInitiateId = None,
      webIdentId = None,
      certifyUserId = None,
      dateOfBirth = BirthDate(dateOfBirth)
    )
  }

}
