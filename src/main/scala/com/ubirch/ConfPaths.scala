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
      final val CONFIG_URL = "keycloak-certify.tokenVerification.configURL"
      final val KID = "keycloak-certify.tokenVerification.kid"
      final val DEFAULT_REALM = "keycloak-certify.defaultRealm"
      final val BMG_REALM = "keycloak-certify.bmgRealm"
      final val UBIRCH_REALM = "keycloak-certify.ubirchRealm"
    }

    object DeviceKeycloak {
      final val SERVER_URL = "keycloak-device.server.url"
      final val SERVER_REALM = "keycloak-device.server.realm"
      final val USERNAME = "keycloak-device.server.username"
      final val PASSWORD = "keycloak-device.server.password"
      final val CLIENT_ID = "keycloak-device.server.clientId"
      final val CONFIG_URL = "keycloak-device.tokenVerification.configURL"
      final val KID = "keycloak-device.tokenVerification.kid"
      final val DEFAULT_REALM = "keycloak-device.defaultRealm"
    }
  }

  trait ServicesConfPaths {
    final val DATA_SCHEMA_GROUP_MAP = "system.services.dataSchemaGroupMap"
    final val POC_TYPE_DATA_SCHEMA_MAP = "system.services.pocTypeDataSchemaMap"
    final val TRUSTED_POC_GROUP_MAP = "system.services.trustedPocGroupMap"
    final val POC_TYPE_ENDPOINT_MAP = "system.services.pocTypeEndpointMap"
    final val POC_TYPES_ROLE_NEEDED = "system.services.pocTypeRoleNeeded"
    final val POC_TYPES_LOCATION_NEEDED = "system.services.pocTypeLocationNeeded"
    final val POC_TYPE_POC_NAME_MAP = "system.services.pocTypePocNameMap"

    //urls and tokens
    final val THING_API_URL_CREATE_DEVICE = "system.services.thingApiURLCreateDevice"
    final val THING_API_URL_GET_INFO = "system.services.thingApiURLGetInfo"
    final val CERT_MANAGER_URL = "system.services.certManagerURL"
    final val CERT_MANAGER_TOKEN = "system.services.certManagerToken"
    final val CERTIFY_API_URL = "system.services.certifyApiURL"
    final val CERTIFY_API_TOKEN = "system.services.certifyApiToken"
    final val GO_CLIENT_URL = "system.services.goClientURL"
    final val GO_CLIENT_TOKEN = "system.services.goClientToken"
    final val POC_CREATION_INTERVAL = "system.services.pocCreationInterval"
    final val POC_LOGO_ENDPOINT = "system.services.pocLogoEndpoint"
    final val POC_ISSUE_CERTS = "system.services.issueCerts"
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

  sealed trait TeamDrivePaths {
    val URL = "system.teamdrive.url"
    val USERNAME = "system.teamdrive.username"
    val PASSWORD = "system.teamdrive.password"
    val READ_TIMEOUT = "system.teamdrive.readTimeout"
    val STAGE = "system.teamdrive.stage"
    val UBIRCH_ADMINS = "system.teamdrive.ubirchAdmins"
    val CERT_WELCOME_MESSAGE = "system.teamdrive.certWelcomeMessage"
    val STATIC_ASSETS_WELCOME_MESSAGE = "system.teamdrive.staticAssetsWelcomeMessage"
    val POC_TYPE_STATIC_SPACE_NAME_MAP = "system.teamdrive.pocTypeStaticSpaceNameMap"
  }

  trait HealthChecks {
    object Timeouts {
      val ELEMENTS_PROCESSING = "healthchecks.timeouts.elementsProcessing"
      val WAITING_FOR_NEW_ELEMENTS = "healthchecks.timeouts.waitingForNewElements"
      val STARTUP = "healthchecks.timeouts.startup"
    }
  }

  trait Lifecycle {
    val GENERAL_TIMEOUT = "system.lifecycle.generalTimeout"
    val LOOP_CANCELLATION_TIMEOUT = "system.lifecycle.loopCancellationTimeout"
  }

  object GenericConfPaths extends GenericConfPaths

  object HttpServerConfPaths extends HttpServerConfPaths

  object KeycloakPaths extends KeycloakPaths

  object ServicesConfPaths extends ServicesConfPaths

  object AESEncryptionPaths extends AESEncryption

  object PostgresPaths extends PostgresPaths

  object TeamDrivePaths extends TeamDrivePaths

  object HealthChecks extends HealthChecks

  object Lifecycle extends Lifecycle
}
