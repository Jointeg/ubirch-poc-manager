package com.ubirch.models.user

case class User(id: UserId, email: Email, status: UserStatus = WaitingForRequiredActions)
