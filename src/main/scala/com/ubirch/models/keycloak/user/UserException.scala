package com.ubirch.models.keycloak.user
import com.ubirch.models.user.UserName

sealed trait UserException
case class UserAlreadyExists(userName: UserName) extends UserException
case class UserCreationError(errorMsg: String) extends UserException
