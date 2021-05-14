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
    object CertifyKeycloak {
      final val SERVER_URL = "keycloak-certify.server.url"
      final val SERVER_REALM = "keycloak-certify.server.realm"
      final val USERNAME = "keycloak-certify.server.username"
      final val PASSWORD = "keycloak-certify.server.password"
      final val CLIENT_ID = "keycloak-certify.server.clientId"
      final val REALM = "keycloak-certify.realm"
      final val CLIENT_CONFIG = "keycloak-certify.client.config"
      final val CLIENT_ADMIN_USER = "keycloak-certify.client.adminUsername"
      final val CLIENT_ADMIN_PASSWORD = "keycloak-certify.client.adminPassword"
      final val USER_POLLING_INTERVAL = "keycloak-certify.polling.interval"
      final val CONFIG_URL = "keycloak-certify.tokenVerification.configURL"
      final val KID = "keycloak-certify.tokenVerification.kid"
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
    final val DATA_SCHEMA_GROUP_MAP = "system.services.dataSchemaGroupMap"
    //urls and tokens
    final val THING_API_URL_CREATE_DEVICE = "system.services.thingApiURLCreateDevice"
    final val THING_API_URL_GET_INFO = "system.services.thingApiURLGetInfo"
    final val CERTIFY_API_URL = "system.services.certifyApiURL"
    final val CERTIFY_API_TOKEN = "system.services.certifyApiToken"
    final val GO_CLIENT_URL = "system.services.goClientURL"
    final val GO_CLIENT_TOKEN = "system.services.goClientToken"
    final val POC_CREATION_INTERVAL = "system.services.pocCreationInterval"
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
