package com.ubirch.services.keycloak

import com.google.inject.{ Inject, Singleton }
import com.typesafe.config.Config
import com.ubirch.ConfPaths

trait KeycloakDeviceConfig {
  def serverUrl: String

  def serverRealm: String

  def username: String

  def password: String

  def clientId: String

  def configUrl: String

  def acceptedKid: String
}

@Singleton
class RealKeycloakDeviceConfig @Inject() (val conf: Config) extends KeycloakDeviceConfig {

  val serverUrl: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.SERVER_URL)
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.CLIENT_ID)
  val configUrl: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.CONFIG_URL)
  val acceptedKid: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.KID)
}
