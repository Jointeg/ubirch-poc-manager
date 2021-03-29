package com.ubirch.models.keycloak.roles

sealed trait RolesException
case class RoleAlreadyExists(roleName: RoleName) extends RolesException
