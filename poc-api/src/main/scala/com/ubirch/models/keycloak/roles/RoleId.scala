package com.ubirch.models.keycloak.roles

import java.util.UUID

case class RoleId(value: String) extends AnyVal

object RoleId {
  def asUUID(): RoleId = RoleId(UUID.randomUUID().toString)
}
