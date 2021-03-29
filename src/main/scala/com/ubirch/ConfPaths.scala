package com.ubirch

object ConfPaths {

  trait GenericConfPaths {
    final val NAME = "system.name"
  }

  trait HttpServerConfPaths {
    final val PORT = "system.server.port"
    final val SWAGGER_PATH = "system.server.swaggerPath"
  }

  trait ExecutionContextConfPaths {
    final val THREAD_POOL_SIZE = "system.executionContext.threadPoolSize"
  }

  trait PrometheusConfPaths {
    final val PORT = "system.metrics.prometheus.port"
  }

  trait TokenVerificationPaths {
    final val CONFIG_URL = "system.tokenVerification.configURL"
    final val KID = "system.tokenVerification.kid"
  }

  trait KeycloakPaths {
    final val SERVER_URL = "keycloak.server.url"
    final val SERVER_REALM = "keycloak.server.realm"
    final val USERNAME = "keycloak.server.username"
    final val PASSWORD = "keycloak.server.password"
    final val CLIENT_ID = "keycloak.server.clientId"
    final val USERS_REALM = "keycloak.users.realm"
  }

  object GenericConfPaths extends GenericConfPaths
  object HttpServerConfPaths extends HttpServerConfPaths
  object TokenVerificationPaths extends TokenVerificationPaths
  object KeycloakPaths extends KeycloakPaths
}
