package com.ubirch.models.keycloak.user
import com.ubirch.models.user.UserName

sealed trait UserException
case class UserAlreadyExists(uerName: UserName) extends UserException
