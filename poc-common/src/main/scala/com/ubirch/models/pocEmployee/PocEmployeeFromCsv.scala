package com.ubirch.models.pocEmployee

import com.ubirch.models.tenant.TenantId
import com.ubirch.models.user.{ Email, FirstName, LastName }

import java.util.UUID

case class PocEmployeeFromCsv(
  firstName: FirstName,
  lastName: LastName,
  email: Email
) {
  def toFullPocEmployeeRepresentation(
    pocId: UUID,
    tenantId: TenantId
  ): PocEmployee = PocEmployee(UUID.randomUUID(), pocId, tenantId, firstName.value, lastName.value, email.value)
}
