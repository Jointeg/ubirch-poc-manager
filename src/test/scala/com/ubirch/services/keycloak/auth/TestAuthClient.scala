package com.ubirch.services.keycloak.auth

import monix.eval.Task

import javax.inject.Singleton

@Singleton
class TestAuthClient() extends AuthClient {
  override def obtainAccessToken(username: String, password: String): Task[String] = Task("random-token")
}
