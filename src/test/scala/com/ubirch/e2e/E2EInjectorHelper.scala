package com.ubirch.e2e
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigFactory }
import com.ubirch._
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.{ KeycloakCertifyConfig, KeycloakDeviceConfig }
import io.getquill.context.monix.MonixJdbcContext.Runner
import io.getquill.{ PostgresJdbcContext, PostgresMonixJdbcContext, SnakeCase }
import monix.eval.Task

import javax.inject.{ Inject, Singleton }

case class KeycloakUsersRuntimeConfig(tenantAdmin: TenantAdmin)

@Singleton
class TestKeycloakCertifyConfig @Inject() (val conf: Config, keycloakRuntimeConfig: KeycloakUsersRuntimeConfig)
  extends KeycloakCertifyConfig {

  private val keycloakServer = KeycloakCertifyContainer.container.container.getContainerIpAddress
  private val keycloakPort = KeycloakCertifyContainer.container.container.getFirstMappedPort

  val serverUrl: String = s"http://$keycloakServer:$keycloakPort/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.CLIENT_ID)
  val realm: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.REALM)
  val clientConfig: String =
    s"""{ "realm": "certify-realm", "auth-server-url": "http://$keycloakServer:$keycloakPort/auth", "ssl-required": "external", "resource": "ubirch-2.0-user-access-local", "credentials": { "secret": "ca942e9b-8336-43a3-bd22-adcaf7e5222f" }, "confidential-port": 0 }"""
  val clientAdminUsername: String = keycloakRuntimeConfig.tenantAdmin.userName.value
  val clientAdminPassword: String = keycloakRuntimeConfig.tenantAdmin.password
  val userPollingInterval: Int = conf.getInt(ConfPaths.KeycloakPaths.CertifyKeycloak.USER_POLLING_INTERVAL)

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
class TestPostgresQuillMonixJdbcContext @Inject() () extends QuillMonixJdbcContext {
  override val ctx: PostgresMonixJdbcContext[SnakeCase] = StaticTestPostgresJdbcContext.ctx

  override def withTransaction[T](f: => Task[T]): Task[T] = f
}

object StaticTestPostgresJdbcContext {
  val ctx: PostgresMonixJdbcContext[SnakeCase] = new PostgresMonixJdbcContext(
    SnakeCase,
    ConfigFactory.parseString(s"""
                                 |    dataSourceClassName = org.postgresql.ds.PGSimpleDataSource
                                 |    dataSource.user = postgres
                                 |    dataSource.password = postgres
                                 |    dataSource.databaseName = postgres
                                 |    dataSource.portNumber = ${PostgresDbContainer.container.container.getFirstMappedPort}
                                 |    dataSource.serverName = ${PostgresDbContainer.container.container.getContainerIpAddress}
                                 |    connectionTimeout = 30000
                                 |""".stripMargin),
    Runner.default)
}

class E2EInjectorHelperImpl(val superAdmin: SuperAdmin, val tenantAdmin: TenantAdmin)
  extends InjectorHelper(List(new Binder {

    override def PublicKeyPoolService: ScopedBindingBuilder = {
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])
    }

    override def QuillMonixJdbcContext: ScopedBindingBuilder =
      bind(classOf[QuillMonixJdbcContext]).to(classOf[TestPostgresQuillMonixJdbcContext])

    override def KeycloakUsersConfig: ScopedBindingBuilder = {
      bind(classOf[KeycloakCertifyConfig]).toConstructor(
        classOf[TestKeycloakCertifyConfig].getConstructor(classOf[Config], classOf[KeycloakUsersRuntimeConfig])
      )
    }

    override def KeycloakDeviceConfig: ScopedBindingBuilder = {
      bind(classOf[KeycloakDeviceConfig]).toConstructor(
        classOf[TestKeycloakDeviceConfig].getConstructor(classOf[Config])
      )
    }

    override def configure(): Unit = {
      super.configure()
      bind(classOf[KeycloakUsersRuntimeConfig]).toInstance(KeycloakUsersRuntimeConfig(tenantAdmin))
    }

  }))
