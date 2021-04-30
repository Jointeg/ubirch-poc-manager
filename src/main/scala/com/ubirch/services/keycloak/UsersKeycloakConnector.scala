package com.ubirch.services.keycloak

import com.google.inject.{ Inject, Singleton }
import org.keycloak.admin.client.Keycloak

trait UsersKeycloakConnector {
  def keycloak: Keycloak
}

@Singleton
class UsersKeycloakConfigConnector @Inject() (keycloakUsersConfig: KeycloakUsersConfig) extends UsersKeycloakConnector {
  val keycloak: Keycloak = {
    Keycloak.getInstance(
      keycloakUsersConfig.serverUrl,
      keycloakUsersConfig.serverRealm,
      keycloakUsersConfig.username,
      keycloakUsersConfig.password,
      keycloakUsersConfig.clientId
    )
  }
}
