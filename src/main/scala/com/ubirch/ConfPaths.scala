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

  trait KeycloakPaths {
    object UsersKeycloak {
      final val SERVER_URL = "keycloak-users.server.url"
      final val SERVER_REALM = "keycloak-users.server.realm"
      final val USERNAME = "keycloak-users.server.username"
      final val PASSWORD = "keycloak-users.server.password"
      final val CLIENT_ID = "keycloak-users.server.clientId"
      final val REALM = "keycloak-users.realm"
      final val CLIENT_CONFIG = "keycloak-users.client.config"
      final val CLIENT_ADMIN_USER = "keycloak-users.client.adminUsername"
      final val CLIENT_ADMIN_PASSWORD = "keycloak-users.client.adminPassword"
      final val USER_POLLING_INTERVAL = "keycloak-users.polling.interval"
      final val CONFIG_URL = "keycloak-users.tokenVerification.configURL"
      final val KID = "keycloak-users.tokenVerification.kid"
    }

    object DeviceKeycloak {
      final val SERVER_URL = "keycloak-device.server.url"
      final val SERVER_REALM = "keycloak-device.server.realm"
      final val USERNAME = "keycloak-device.server.username"
      final val PASSWORD = "keycloak-device.server.password"
      final val CLIENT_ID = "keycloak-device.server.clientId"
      final val REALM = "keycloak-device.realm"
      final val CONFIG_URL = "keycloak-device.tokenVerification.configURL"
      final val KID = "keycloak-device.tokenVerification.kid"
    }
  }

  trait ServicesConfPaths {
    final val DATA_SCHEMA_GROUP_IDS = "system.services.dataSchemaGroupIds"
    //authorization roles
    final val SUPER_ADMIN_ROLE = "system.services.superAdminRole"
    final val TENANT_ADMIN_ROLE = "system.services.tenantAdminRole"
    final val POC_ADMIN_ROLE = "system.services.pocAdminRole"
    //urls and tokens
    final val THING_API_URL = "system.services.thingApiURL"
    final val CERTIFY_API_URL = "system.services.certifyApiURL"
    final val CERTIFY_API_TOKEN = "system.services.certifyApiToken"
    final val GO_CLIENT_URL = "system.services.goClientURL"
    final val GO_CLIENT_TOKEN = "system.services.goClientToken"
  }

  trait AESEncryption {
    final val SECRET_KEY = "system.aesEncryption.secretKey"
  }

  trait PostgresPaths {
    final val SERVER_NAME = "database.dataSource.serverName"
    final val USER = "database.dataSource.user"
    final val PASSWORD = "database.dataSource.password"
    final val PORT = "database.dataSource.portNumber"
    final val DATABASE_NAME = "database.dataSource.databaseName"
  }

  object GenericConfPaths extends GenericConfPaths

  object HttpServerConfPaths extends HttpServerConfPaths

  object KeycloakPaths extends KeycloakPaths

  object ServicesConfPaths extends ServicesConfPaths

  object AESEncryptionPaths extends AESEncryption

  object PostgresPaths extends PostgresPaths
}
