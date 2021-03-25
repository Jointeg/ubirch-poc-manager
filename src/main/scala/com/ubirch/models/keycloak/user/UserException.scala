package com.ubirch.models.keycloak.user

sealed trait UserException
case class UserAlreadyExists(uerName: UserName) extends UserException
