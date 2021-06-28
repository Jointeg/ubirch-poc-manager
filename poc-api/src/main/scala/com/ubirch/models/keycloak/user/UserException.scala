package com.ubirch.models.keycloak.user

sealed trait UserException
case class UserAlreadyExists(user: String) extends UserException
case class UserCreationError(errorMsg: String) extends UserException
