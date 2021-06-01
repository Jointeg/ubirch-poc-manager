package com.ubirch.services

import com.ubirch.services.keycloak.{CertifyDefaultRealm, CertifyKeycloakConnector, DeviceDefaultRealm, DeviceKeycloakConnector, KeycloakCertifyConfig, KeycloakDeviceConfig, KeycloakRealm}
import org.keycloak.admin.client.Keycloak

import javax.inject.Inject

trait KeycloakConnector {
  def getKeycloak(keycloakInstance: KeycloakInstance): Keycloak

  def getKeycloakRealm(keycloakInstance: KeycloakInstance): String
}

class DefaultKeycloakConnector @Inject() (
  certifyKeycloakConnector: CertifyKeycloakConnector,
  deviceKeycloakConnector: DeviceKeycloakConnector,
  keycloakCertifyConfig: KeycloakCertifyConfig,
  keycloakDeviceConfig: KeycloakDeviceConfig)
  extends KeycloakConnector {
  override def getKeycloak(keycloakInstance: KeycloakInstance): Keycloak =
    keycloakInstance match {
      case CertifyKeycloak => certifyKeycloakConnector.keycloak
      case DeviceKeycloak  => deviceKeycloakConnector.keycloak
    }

  // @todo delete it
  override def getKeycloakRealm(keycloakInstance: KeycloakInstance): String =
    keycloakInstance match {
      case CertifyKeycloak => keycloakCertifyConfig.realm
      case DeviceKeycloak  => keycloakDeviceConfig.realm
    }
}

sealed trait KeycloakInstance {
  val name: String
  val defaultRealm: KeycloakRealm
}

case object CertifyKeycloak extends KeycloakInstance {
  val name = "certify keycloak"
  val defaultRealm = CertifyDefaultRealm
}

case object DeviceKeycloak extends KeycloakInstance {
  val name = "device keycloak"
  val defaultRealm = DeviceDefaultRealm
}
