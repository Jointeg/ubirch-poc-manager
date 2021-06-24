package com.ubirch.controllers.model

import com.ubirch.models.poc.Status
import com.ubirch.models.pocEmployee.PocEmployee
import org.joda.time.DateTime

object PocAdminControllerJsonModel {

  case class PocEmployee_OUT(
    id: String,
    firstName: String,
    lastName: String,
    email: String,
    active: Boolean,
    status: Status,
    revokeTime: Option[DateTime],
    createdAt: DateTime)

  case class PocEmployee_IN(firstName: String, lastName: String, email: String) {
    def copyToPocEmployee(pe: PocEmployee): PocEmployee = pe.copy(name = firstName, surname = lastName, email = email)
  }

  object PocEmployee_OUT {
    def fromPocEmployee(pocEmployee: PocEmployee): PocEmployee_OUT =
      PocEmployee_OUT(
        id = pocEmployee.id.toString,
        firstName = pocEmployee.name,
        lastName = pocEmployee.surname,
        email = pocEmployee.email,
        active = pocEmployee.active,
        status = pocEmployee.status,
        revokeTime = pocEmployee.webAuthnDisconnected,
        createdAt = pocEmployee.created.dateTime
      )
  }
}
