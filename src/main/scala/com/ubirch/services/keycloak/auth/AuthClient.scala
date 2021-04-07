package com.ubirch.services.keycloak.auth

import com.google.inject.{Inject, Singleton}
import com.ubirch.services.keycloak.KeycloakConfig
import org.keycloak.authorization.client.AuthzClient

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@Singleton
class AuthClient @Inject() (keycloakConfig: KeycloakConfig) {

  val client = createAuthorisationClient(keycloakConfig.jsonString)

  private def createAuthorisationClient(keyCloakJson: String): AuthzClient = {
    val jsonKeycloakStream = new ByteArrayInputStream(keyCloakJson.getBytes(StandardCharsets.UTF_8))
    AuthzClient.create(jsonKeycloakStream)
  }

}
