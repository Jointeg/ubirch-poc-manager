package com.ubirch.services.keycloak

import com.google.inject.{Inject, Singleton}
import com.ubirch.ConfPaths
import com.ubirch.services.config.ConfigProvider

@Singleton
class KeycloakConfig @Inject() (val configProvider: ConfigProvider) {

  val serverUrl: String = configProvider.conf.getString(ConfPaths.KeycloakPaths.SERVER_URL)
  val serverRealm: String = configProvider.conf.getString(ConfPaths.KeycloakPaths.SERVER_REALM)
  val username: String = configProvider.conf.getString(ConfPaths.KeycloakPaths.USERNAME)
  val password: String = configProvider.conf.getString(ConfPaths.KeycloakPaths.PASSWORD)
  val clientId: String = configProvider.conf.getString(ConfPaths.KeycloakPaths.CLIENT_ID)

}
