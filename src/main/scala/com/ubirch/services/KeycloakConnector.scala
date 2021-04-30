package com.ubirch.services

import com.ubirch.services.keycloak.{
  DeviceKeycloakConnector,
  KeycloakDeviceConfig,
  KeycloakUsersConfig,
  UsersKeycloakConnector
}
import org.keycloak.admin.client.Keycloak

import javax.inject.Inject

trait KeycloakConnector {
  def getKeycloak(keycloakInstance: KeycloakInstance): Keycloak

  def getKeycloakRealm(keycloakInstance: KeycloakInstance): String
}

class DefaultKeycloakConnector @Inject() (
  usersKeycloakConnector: UsersKeycloakConnector,
  deviceKeycloakConnector: DeviceKeycloakConnector,
  keycloakUsersConfig: KeycloakUsersConfig,
  keycloakDeviceConfig: KeycloakDeviceConfig)
  extends KeycloakConnector {
  override def getKeycloak(keycloakInstance: KeycloakInstance): Keycloak =
    keycloakInstance match {
      case UsersKeycloak  => usersKeycloakConnector.keycloak
      case DeviceKeycloak => deviceKeycloakConnector.keycloak
    }

  override def getKeycloakRealm(keycloakInstance: KeycloakInstance): String =
    keycloakInstance match {
      case UsersKeycloak  => keycloakUsersConfig.realm
      case DeviceKeycloak => keycloakDeviceConfig.realm
    }
}

sealed trait KeycloakInstance

case object UsersKeycloak extends KeycloakInstance

case object DeviceKeycloak extends KeycloakInstance
