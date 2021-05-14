package com.ubirch.e2e
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigFactory }
import com.ubirch._
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.{ KeycloakDeviceConfig, KeycloakUsersConfig }
import com.ubirch.services.poc.{ CertHandler, TestCertHandler }
import io.getquill.{ PostgresJdbcContext, SnakeCase }

import javax.inject.{ Inject, Singleton }

case class KeycloakUsersRuntimeConfig(tenantAdmin: TenantAdmin)

@Singleton
class TestKeycloakUsersConfig @Inject() (val conf: Config, keycloakRuntimeConfig: KeycloakUsersRuntimeConfig)
  extends KeycloakUsersConfig {

  private val keycloakServer = KeycloakUsersContainer.container.container.getContainerIpAddress
  private val keycloakPort = KeycloakUsersContainer.container.container.getFirstMappedPort

  val serverUrl: String = s"http://$keycloakServer:$keycloakPort/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.CLIENT_ID)
  val realm: String = conf.getString(ConfPaths.KeycloakPaths.UsersKeycloak.REALM)
  val clientConfig: String =
    s"""{ "realm": "users-realm", "auth-server-url": "http://$keycloakServer:$keycloakPort/auth", "ssl-required": "external", "resource": "ubirch-2.0-user-access-local", "credentials": { "secret": "ca942e9b-8336-43a3-bd22-adcaf7e5222f" }, "confidential-port": 0 }"""
  val clientAdminUsername: String = keycloakRuntimeConfig.tenantAdmin.userName.value
  val clientAdminPassword: String = keycloakRuntimeConfig.tenantAdmin.password
  val userPollingInterval: Int = conf.getInt(ConfPaths.KeycloakPaths.UsersKeycloak.USER_POLLING_INTERVAL)

}

@Singleton
class TestKeycloakDeviceConfig @Inject() (val conf: Config) extends KeycloakDeviceConfig {

  private val keycloakServer = KeycloakDeviceContainer.container.container.getContainerIpAddress
  private val keycloakPort = KeycloakDeviceContainer.container.container.getFirstMappedPort

  val serverUrl: String = s"http://$keycloakServer:$keycloakPort/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.CLIENT_ID)
  val realm: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.REALM)
}

@Singleton
class TestPostgresQuillJdbcContext @Inject() () extends QuillJdbcContext {
  override val ctx: PostgresJdbcContext[SnakeCase] = StaticTestPostgresJdbcContext.ctx
}

object StaticTestPostgresJdbcContext {
  val ctx: PostgresJdbcContext[SnakeCase] = new PostgresJdbcContext(
    SnakeCase,
    ConfigFactory.parseString(s"""
                                 |    dataSourceClassName = org.postgresql.ds.PGSimpleDataSource
                                 |    dataSource.user = postgres
                                 |    dataSource.password = postgres
                                 |    dataSource.databaseName = postgres
                                 |    dataSource.portNumber = ${PostgresDbContainer.container.container.getFirstMappedPort}
                                 |    dataSource.serverName = ${PostgresDbContainer.container.container.getContainerIpAddress}
                                 |    connectionTimeout = 30000
                                 |""".stripMargin))
}

class E2EInjectorHelperImpl(val superAdmin: SuperAdmin, val tenantAdmin: TenantAdmin)
  extends InjectorHelper(List(new Binder {

    override def PublicKeyPoolService: ScopedBindingBuilder = {
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])
    }

    override def QuillJdbcContext: ScopedBindingBuilder =
      bind(classOf[QuillJdbcContext]).to(classOf[TestPostgresQuillJdbcContext])

    override def KeycloakUsersConfig: ScopedBindingBuilder = {
      bind(classOf[KeycloakUsersConfig]).toConstructor(
        classOf[TestKeycloakUsersConfig].getConstructor(classOf[Config], classOf[KeycloakUsersRuntimeConfig])
      )
    }

    override def KeycloakDeviceConfig: ScopedBindingBuilder = {
      bind(classOf[KeycloakDeviceConfig]).toConstructor(
        classOf[TestKeycloakDeviceConfig].getConstructor(classOf[Config])
      )
    }

    override def CertHandler: ScopedBindingBuilder =
      bind(classOf[CertHandler]).to(classOf[TestCertHandler])

    override def configure(): Unit = {
      super.configure()
      bind(classOf[KeycloakUsersRuntimeConfig]).toInstance(KeycloakUsersRuntimeConfig(tenantAdmin))
    }

  }))
