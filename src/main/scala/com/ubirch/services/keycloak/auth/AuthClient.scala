package com.ubirch.services.keycloak.auth

import com.google.inject.{ Inject, Singleton }
import com.ubirch.services.keycloak.KeycloakCertifyConfig
import monix.eval.Task
import org.keycloak.authorization.client.AuthzClient

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

trait AuthClient {
  def obtainAccessToken(username: String, password: String): Task[String]
}

@Singleton
class KeycloakAuthzClient @Inject() (keycloakUsersConfig: KeycloakCertifyConfig) extends AuthClient {

  private val client: AuthzClient = createAuthorisationClient(keycloakUsersConfig.clientConfig)

  override def obtainAccessToken(username: String, password: String): Task[String] =
    Task {
      client
        .obtainAccessToken(username, password)
        .getToken
    }

  private def createAuthorisationClient(keyCloakJson: String): AuthzClient = {
    val jsonKeycloakStream = new ByteArrayInputStream(keyCloakJson.getBytes(StandardCharsets.UTF_8))
    AuthzClient.create(jsonKeycloakStream)
  }

}
