package com.ubirch.models.keycloak.group

import com.ubirch.models.tenant

import java.util.UUID

case class GroupId(value: String) extends AnyVal {
  def toTenantGroupId: tenant.GroupId = com.ubirch.models.tenant.GroupId(UUID.fromString(value))
}
