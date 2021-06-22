package com.ubirch.services.keycloak

import com.google.inject.{ Inject, Singleton }
import org.keycloak.admin.client.Keycloak

trait CertifyKeycloakConnector {
  def keycloak: Keycloak
}

@Singleton
class CertifyKeycloakConfigConnector @Inject() (keycloakUsersConfig: KeycloakCertifyConfig)
  extends CertifyKeycloakConnector {
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
