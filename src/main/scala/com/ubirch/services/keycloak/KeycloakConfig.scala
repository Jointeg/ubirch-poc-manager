package com.ubirch.services.keycloak

import com.google.inject.{Inject, Singleton}
import com.typesafe.config.Config
import com.ubirch.ConfPaths

@Singleton
class KeycloakConfig @Inject() (val conf: Config) {

  val serverUrl: String = conf.getString(ConfPaths.KeycloakPaths.SERVER_URL)
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.CLIENT_ID)
  val usersRealm: String = conf.getString(ConfPaths.KeycloakPaths.USERS_REALM)

}
