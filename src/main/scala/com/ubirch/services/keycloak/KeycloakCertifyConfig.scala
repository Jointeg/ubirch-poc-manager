package com.ubirch.services.keycloak

import com.google.inject.{ Inject, Singleton }
import com.typesafe.config.Config
import com.ubirch.ConfPaths

trait KeycloakCertifyConfig {
  def serverUrl: String

  def serverRealm: String

  def username: String

  def password: String

  def clientId: String

  def realm: String

  def configUrl: String

  def acceptedKid: String
}

@Singleton
class RealKeycloakCertifyConfig @Inject() (val conf: Config) extends KeycloakCertifyConfig {

  val serverUrl: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.SERVER_URL)
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.CLIENT_ID)
  val realm: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.REALM)
  val configUrl: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.CONFIG_URL)
  val acceptedKid: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.KID)
}
