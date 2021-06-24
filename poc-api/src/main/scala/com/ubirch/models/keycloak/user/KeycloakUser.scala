package com.ubirch.models.keycloak.user

import com.ubirch.models.user.{ Email, FirstName, LastName, UserId, UserName }

case class KeycloakUser(
  id: UserId,
  username: UserName,
  enabled: Enabled,
  emailVerified: EmailVerified,
  firstName: FirstName,
  lastName: LastName,
  email: Email,
  attributes: Map[String, List[String]]
)

case class Enabled(value: Boolean) extends AnyVal
case class EmailVerified(value: Boolean) extends AnyVal
