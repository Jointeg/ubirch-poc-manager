package com.ubirch.e2e
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch._
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.{KeycloakDeviceConfig, KeycloakUsersConfig}
import io.getquill.{PostgresJdbcContext, SnakeCase}

import javax.inject.{Inject, Singleton}

case class KeycloakUsersRuntimeConfig(server: String, port: Int, tenantAdmin: TenantAdmin)
case class KeycloakDeviceRuntimeConfig(server: String, port: Int, superAdmin: SuperAdmin)
case class PostgresRuntimeConfig(server: String, port: Int)

@Singleton
class TestKeycloakUsersConfig @Inject() (val conf: Config, keycloakRuntimeConfig: KeycloakUsersRuntimeConfig)
  extends KeycloakUsersConfig {

  val serverUrl: String = s"http://${keycloakRuntimeConfig.server}:${keycloakRuntimeConfig.port}/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.CLIENT_ID)
  val realm: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.REALM)
  val clientConfig: String =
    s"""{ "realm": "users-realm", "auth-server-url": "http://${keycloakRuntimeConfig.server}:${keycloakRuntimeConfig.port}/auth", "ssl-required": "external", "resource": "ubirch-2.0-user-access-local", "credentials": { "secret": "ca942e9b-8336-43a3-bd22-adcaf7e5222f" }, "confidential-port": 0 }"""
  val clientAdminUsername: String = keycloakRuntimeConfig.tenantAdmin.userName.value
  val clientAdminPassword: String = keycloakRuntimeConfig.tenantAdmin.password
  val userPollingInterval: Int = conf.getInt(ConfPaths.KeycloakPaths.UsersKeycloak.USER_POLLING_INTERVAL)

}

@Singleton
class TestKeycloakDeviceConfig @Inject() (val conf: Config, keycloakRuntimeConfig: KeycloakDeviceRuntimeConfig)
  extends KeycloakDeviceConfig {

  val serverUrl: String = s"http://${keycloakRuntimeConfig.server}:${keycloakRuntimeConfig.port}/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.CLIENT_ID)
  val realm: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.REALM)
  val clientConfig: String =
    s"""{ "realm": "device-realm", "auth-server-url": "http://${keycloakRuntimeConfig.server}:${keycloakRuntimeConfig.port}/auth", "ssl-required": "external", "resource": "ubirch-2.0-user-access-local", "credentials": { "secret": "ca942e9b-8336-43a3-bd22-adcaf7e5222f" }, "confidential-port": 0 }"""
  val clientAdminUsername: String = keycloakRuntimeConfig.superAdmin.userName.value
  val clientAdminPassword: String = keycloakRuntimeConfig.superAdmin.password
}

@Singleton
class TestPostgresQuillJdbcContext @Inject() (val postgresRuntimeConfig: PostgresRuntimeConfig)
  extends QuillJdbcContext {
  override val ctx: PostgresJdbcContext[SnakeCase] = new PostgresJdbcContext(
    SnakeCase,
    ConfigFactory.parseString(s"""
      |    dataSourceClassName = org.postgresql.ds.PGSimpleDataSource
      |    dataSource.user = postgres
      |    dataSource.password = postgres
      |    dataSource.databaseName = postgres
      |    dataSource.portNumber = ${postgresRuntimeConfig.port}
      |    dataSource.serverName = ${postgresRuntimeConfig.server}
      |    connectionTimeout = 30000
      |""".stripMargin))
}

class E2EInjectorHelperImpl(
  val postgreContainer: PostgresContainer,
  val keycloakUsersContainer: KeycloakContainer,
  val keycloakDeviceContainer: KeycloakContainer,
  val superAdmin: SuperAdmin,
  val tenantAdmin: TenantAdmin)
  extends InjectorHelper(List(new Binder {

    override def PublicKeyPoolService: ScopedBindingBuilder = {
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])
    }

    override def QuillJdbcContext: ScopedBindingBuilder =
      bind(classOf[QuillJdbcContext]).toConstructor(
        classOf[TestPostgresQuillJdbcContext].getConstructor(classOf[PostgresRuntimeConfig]))

    override def KeycloakUsersConfig: ScopedBindingBuilder = {
      bind(classOf[KeycloakUsersConfig]).toConstructor(
        classOf[TestKeycloakUsersConfig].getConstructor(classOf[Config], classOf[KeycloakUsersRuntimeConfig])
      )
    }

    override def KeycloakDeviceConfig: ScopedBindingBuilder = {
      bind(classOf[KeycloakDeviceConfig]).toConstructor(
        classOf[TestKeycloakDeviceConfig].getConstructor(classOf[Config], classOf[KeycloakDeviceRuntimeConfig])
      )
    }

    override def configure(): Unit = {
      super.configure()
      bind(classOf[PostgresRuntimeConfig]).toInstance(
        PostgresRuntimeConfig(
          postgreContainer.container.getContainerIpAddress,
          postgreContainer.container.getFirstMappedPort))
      bind(classOf[KeycloakUsersRuntimeConfig]).toInstance(
        KeycloakUsersRuntimeConfig(
          keycloakUsersContainer.container.getContainerIpAddress,
          keycloakUsersContainer.container.getFirstMappedPort,
          tenantAdmin))
      bind(classOf[KeycloakDeviceRuntimeConfig]).toInstance(
        KeycloakDeviceRuntimeConfig(
          keycloakDeviceContainer.container.getContainerIpAddress,
          keycloakDeviceContainer.container.getFirstMappedPort,
          superAdmin))
    }

  }))
