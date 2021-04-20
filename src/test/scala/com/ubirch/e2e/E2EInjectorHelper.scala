package com.ubirch.e2e
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.crypto.PrivKey
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.{KeycloakConfig, KeycloakConnector}
import com.ubirch._
import io.getquill.{PostgresJdbcContext, SnakeCase}
import org.keycloak.admin.client.Keycloak

import javax.inject.{Inject, Singleton}

case class KeycloakRuntimeConfig(server: String, port: Int, clientAdmin: ClientAdmin)
case class PostgresRuntimeConfig(server: String, port: Int)

@Singleton
class KeycloakDynamicPortConnector @Inject() (keycloakRuntimeConfig: KeycloakRuntimeConfig) extends KeycloakConnector {
  override val keycloak: Keycloak = Keycloak.getInstance(
    s"http://${keycloakRuntimeConfig.server}:${keycloakRuntimeConfig.port}/auth",
    "master",
    "admin",
    "admin",
    "admin-cli"
  )
}

@Singleton
class TestKeycloakConfig @Inject() (val conf: Config, keycloakRuntimeConfig: KeycloakRuntimeConfig)
  extends KeycloakConfig {

  val serverUrl: String = s"http://${keycloakRuntimeConfig.server}:${keycloakRuntimeConfig.port}/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.CLIENT_ID)
  val usersRealm: String = conf.getString(ConfPaths.KeycloakPaths.USERS_REALM)
  val clientConfig: String =
    s"""{ "realm": "test-realm", "auth-server-url": "http://${keycloakRuntimeConfig.server}:${keycloakRuntimeConfig.port}/auth", "ssl-required": "external", "resource": "ubirch-2.0-user-access-local", "credentials": { "secret": "ca942e9b-8336-43a3-bd22-adcaf7e5222f" }, "confidential-port": 0 }"""
  val clientAdminUsername: String = keycloakRuntimeConfig.clientAdmin.userName.value
  val clientAdminPassword: String = keycloakRuntimeConfig.clientAdmin.password
  val userPollingInterval: Int = conf.getInt(ConfPaths.KeycloakPaths.USER_POLLING_INTERVAL)

}

class InjectorHelperImpl()
  extends InjectorHelper(List(new Binder {
    override def PublicKeyPoolService: ScopedBindingBuilder = {
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])
    }

    override def configure(): Unit = {
      super.configure()
      bind(classOf[PrivKey]).toProvider(classOf[KeyPairProvider])
    }
  }))

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
  val keycloakContainer: KeycloakContainer,
  val clientAdmin: ClientAdmin)
  extends InjectorHelper(List(new Binder {

    override def PublicKeyPoolService: ScopedBindingBuilder = {
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])
    }

    override def QuillJdbcContext: ScopedBindingBuilder =
      bind(classOf[QuillJdbcContext]).toConstructor(
        classOf[TestPostgresQuillJdbcContext].getConstructor(classOf[PostgresRuntimeConfig]))

    override def KeycloakConnector: ScopedBindingBuilder = {
      bind(classOf[KeycloakConnector]).toConstructor(
        classOf[KeycloakDynamicPortConnector].getConstructor(classOf[KeycloakRuntimeConfig]))
    }

    override def KeycloakConfig: ScopedBindingBuilder = {
      bind(classOf[KeycloakConfig]).toConstructor(
        classOf[TestKeycloakConfig].getConstructor(classOf[Config], classOf[KeycloakRuntimeConfig])
      )
    }

    override def configure(): Unit = {
      bind(classOf[PostgresRuntimeConfig]).toInstance(
        PostgresRuntimeConfig(
          postgreContainer.container.getContainerIpAddress,
          postgreContainer.container.getFirstMappedPort))
      bind(classOf[KeycloakRuntimeConfig]).toInstance(
        KeycloakRuntimeConfig(
          keycloakContainer.container.getContainerIpAddress,
          keycloakContainer.container.getFirstMappedPort,
          clientAdmin))
      bind(classOf[PrivKey]).toProvider(classOf[KeyPairProvider])
      super.configure()
    }

  }))
