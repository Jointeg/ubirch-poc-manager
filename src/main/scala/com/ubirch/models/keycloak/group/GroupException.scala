package com.ubirch.models.keycloak.group

sealed trait GroupException
case class GroupAlreadyExists(groupName: GroupName) extends GroupException
case class GroupNotFound(groupName: GroupName) extends GroupException
