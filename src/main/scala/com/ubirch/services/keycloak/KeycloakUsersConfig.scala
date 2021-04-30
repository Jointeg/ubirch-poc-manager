package com.ubirch.services.keycloak

import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import com.ubirch.ConfPaths

trait KeycloakUsersConfig {
  def serverUrl: String

  def serverRealm: String

  def username: String

  def password: String

  def clientId: String

  def realm: String

  def clientConfig: String

  def clientAdminUsername: String

  def clientAdminPassword: String

  def userPollingInterval: Int
}

@Singleton
class RealKeycloakUsersConfig @Inject()(val conf: Config) extends KeycloakUsersConfig {

  val serverUrl: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.SERVER_URL)
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.CLIENT_ID)
  val realm: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.REALM)
  val clientConfig: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.CLIENT_CONFIG)
  val clientAdminUsername: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.CLIENT_ADMIN_USER)
  val clientAdminPassword: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.CLIENT_ADMIN_PASSWORD)
  val userPollingInterval: Int = conf.getInt(ConfPaths.KeycloakPaths.UsersKeycloak.USER_POLLING_INTERVAL)

}
