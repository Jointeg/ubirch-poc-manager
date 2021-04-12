package com.ubirch.services.keycloak

import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import com.ubirch.ConfPaths

trait KeycloakConfig {
  def serverUrl: String
  def serverRealm: String
  def username: String
  def password: String
  def clientId: String
  def usersRealm: String
  def clientConfig: String
  def clientAdminUsername: String
  def clientAdminPassword: String
  def userPollingInterval: Int
}

@Singleton
class RealKeycloakConfig @Inject() (val conf: Config) extends KeycloakConfig {

  val serverUrl: String = conf.getString(ConfPaths.KeycloakPaths.SERVER_URL)
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.CLIENT_ID)
  val usersRealm: String = conf.getString(ConfPaths.KeycloakPaths.USERS_REALM)
  val clientConfig: String = conf.getString(ConfPaths.KeycloakPaths.CLIENT_CONFIG)
  val clientAdminUsername: String = conf.getString(ConfPaths.KeycloakPaths.CLIENT_ADMIN_USER)
  val clientAdminPassword: String = conf.getString(ConfPaths.KeycloakPaths.CLIENT_ADMIN_PASSWORD)
  val userPollingInterval: Int = conf.getInt(ConfPaths.KeycloakPaths.USER_POLLING_INTERVAL)

}
