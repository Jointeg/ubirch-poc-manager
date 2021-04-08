package com.ubirch.services.keycloak.auth

import com.google.inject.{Inject, Singleton}
import com.ubirch.services.keycloak.KeycloakConfig
import org.keycloak.authorization.client.AuthzClient

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

trait AuthClient {
  def client: AuthzClient
}

@Singleton
class KeycloakAuthzClient @Inject() (keycloakConfig: KeycloakConfig) extends AuthClient {

  val client: AuthzClient = createAuthorisationClient(keycloakConfig.clientConfig)

  private def createAuthorisationClient(keyCloakJson: String): AuthzClient = {
    val jsonKeycloakStream = new ByteArrayInputStream(keyCloakJson.getBytes(StandardCharsets.UTF_8))
    AuthzClient.create(jsonKeycloakStream)
  }

}
