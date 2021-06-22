package com.ubirch.models.keycloak.group

sealed trait GroupException
case class GroupNotFound(groupName: GroupName) extends GroupException
sealed trait GroupCreationException extends GroupException
case class GroupAlreadyExists(groupName: GroupName) extends GroupCreationException
case class GroupCreationError(errorMsg: String) extends GroupCreationException
