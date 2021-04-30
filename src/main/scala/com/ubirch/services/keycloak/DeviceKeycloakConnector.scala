package com.ubirch.services.keycloak

import com.google.inject.{Inject, Singleton}
import org.keycloak.admin.client.Keycloak

trait DeviceKeycloakConnector {
  def keycloak: Keycloak
}

@Singleton
class DeviceKeycloakConfigConnector @Inject() (keycloakDeviceConfig: KeycloakDeviceConfig)
  extends DeviceKeycloakConnector {
  val keycloak: Keycloak = {
    Keycloak.getInstance(
      keycloakDeviceConfig.serverUrl,
      keycloakDeviceConfig.serverRealm,
      keycloakDeviceConfig.username,
      keycloakDeviceConfig.password,
      keycloakDeviceConfig.clientId
    )
  }
}
