package com.ubirch.services.keycloak

import com.ubirch.ConfPaths.KeycloakPaths
import com.ubirch.services.config.DefaultConfig

sealed trait KeycloakRealm {
  val name: String
}

case object CertifyDefaultRealm extends KeycloakRealm {
  val name = DefaultConfig.conf.getString(KeycloakPaths.CertifyKeycloak.DEFAULT_REALM)
}

case object CertifyBmgRealm extends KeycloakRealm {
  val name = DefaultConfig.conf.getString(KeycloakPaths.CertifyKeycloak.BMG_REALM)
}

case object CertifyUbirchRealm extends KeycloakRealm {
  val name = DefaultConfig.conf.getString(KeycloakPaths.CertifyKeycloak.UBIRCH_REALM)
}

case object DeviceDefaultRealm extends KeycloakRealm {
  val name = DefaultConfig.conf.getString(KeycloakPaths.DeviceKeycloak.DEFAULT_REALM)
}
