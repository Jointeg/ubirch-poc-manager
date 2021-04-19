package com.ubirch.db.models
import com.ubirch.models.user.Email

import java.util.UUID

case class User(id: UUID, email: Email, status: UserStatus = WaitingForRequiredActions)
