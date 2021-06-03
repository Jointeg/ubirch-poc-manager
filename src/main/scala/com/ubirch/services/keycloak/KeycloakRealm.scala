package com.ubirch.services.keycloak

sealed trait KeycloakRealm {
  val name: String
}

case object CertifyDefaultRealm extends KeycloakRealm {
  val name = "poc-certify"
}

case object CertifyBmgRealm extends KeycloakRealm {
  val name = "bmg-certify"
}

case object CertifyUbirchRealm extends KeycloakRealm {
  val name = "default-certify"
}

case object DeviceDefaultRealm extends KeycloakRealm {
  val name = "ubirch-default-realm"
}