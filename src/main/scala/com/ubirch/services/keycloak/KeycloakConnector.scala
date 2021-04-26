package com.ubirch.services.keycloak

import com.google.inject.{ Inject, Singleton }
import org.keycloak.admin.client.Keycloak

trait KeycloakConnector {
  def keycloak: Keycloak
}

@Singleton
class KeycloakConfigConnector @Inject() (keycloakConfig: KeycloakConfig) extends KeycloakConnector {
  val keycloak: Keycloak = {
    Keycloak.getInstance(
      keycloakConfig.serverUrl,
      keycloakConfig.serverRealm,
      keycloakConfig.username,
      keycloakConfig.password,
      keycloakConfig.clientId
    )
  }
}
