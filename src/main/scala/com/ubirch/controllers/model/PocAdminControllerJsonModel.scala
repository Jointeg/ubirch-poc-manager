package com.ubirch.controllers.model

import com.ubirch.models.poc.Status
import com.ubirch.models.pocEmployee.PocEmployee

object PocAdminControllerJsonModel {

  case class PocEmployee_OUT(
    id: String,
    firstName: String,
    lastName: String,
    email: String,
    active: Boolean,
    status: Status)

  case class PocEmployee_IN(firstName: String, lastName: String) {
    def copyToPocEmployee(pe: PocEmployee): PocEmployee = pe.copy(name = firstName, surname = lastName)
  }

  object PocEmployee_OUT {
    def fromPocEmployee(pocEmployee: PocEmployee): PocEmployee_OUT =
      PocEmployee_OUT(
        id = pocEmployee.id.toString,
        firstName = pocEmployee.name,
        lastName = pocEmployee.surname,
        email = pocEmployee.email,
        active = pocEmployee.active,
        status = pocEmployee.status
      )
  }
}
