package com.ubirch

import com.dimafeng.testcontainers.PostgreSQLContainer
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{GeneratorKeyFactory, PrivKey}
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.services.jwt.{
  DefaultPublicKeyPoolService,
  PublicKeyDiscoveryService,
  PublicKeyPoolService,
  TokenCreationService
}
import com.ubirch.services.keycloak.{KeycloakConfig, KeycloakConnector}
import io.getquill.{PostgresJdbcContext, SnakeCase}
import monix.eval.Task
import org.keycloak.admin.client.Keycloak

import java.security.Key
import javax.inject.{Inject, Provider, Singleton}

@Singleton
class FakeDefaultPublicKeyPoolService @Inject() (
  privKey: PrivKey,
  config: Config,
  publicKeyDiscoveryService: PublicKeyDiscoveryService)
  extends DefaultPublicKeyPoolService(config, publicKeyDiscoveryService) {

  override def getKeyFromDiscoveryService(kid: String): Task[Option[Key]] =
    Task {
      kid match {
        case "6dMHOUfu7v6howP2WH5bkp-j9UgUYdyEQbWJp8cb8IY" => Some(privKey.getPublicKey)
        case _ => throw new Exception("Check my kid!")
      }
    }
}

@Singleton
class KeyPairProvider extends Provider[PrivKey] {
  val privKey: PrivKey = GeneratorKeyFactory.getPrivKey(Curve.PRIME256V1)

  override def get(): PrivKey = privKey
}

case class FakeToken(value: String) {
  def prepare: String = "bearer " + value
}

object FakeToken {

  val header: String =
    """
      |{
      |  "alg": "ES256",
      |  "typ": "JWT",
      |  "kid": "6dMHOUfu7v6howP2WH5bkp-j9UgUYdyEQbWJp8cb8IY"
      |}""".stripMargin

  val admin: String =
    """
      |{
      |  "exp": 1718338181,
      |  "iat": 1604336381,
      |  "jti": "2fb1c61d-2113-4b8e-9432-97c28c697b98",
      |  "iss": "https://id.dev.ubirch.com/auth/realms/ubirch-default-realm",
      |  "aud": "account",
      |  "sub": "963995ed-ce12-4ea5-89dc-b181701d1d7b",
      |  "typ": "Bearer",
      |  "azp": "ubirch-2.0-user-access",
      |  "session_state": "f334122a-4693-4826-a2c0-546391886eda",
      |  "acr": "1",
      |  "allowed-origins": [
      |    "http://localhost:9101",
      |    "https://console.dev.ubirch.com"
      |  ],
      |  "realm_access": {
      |    "roles": [
      |      "offline_access",
      |      "ADMIN",
      |      "uma_authorization",
      |      "USER"
      |    ]
      |  },
      |  "resource_access": {
      |    "account": {
      |      "roles": [
      |        "manage-account",
      |        "manage-account-links",
      |        "view-profile"
      |      ]
      |    }
      |  },
      |  "scope": "fav_color profile email",
      |  "email_verified": true,
      |  "fav_fruit": [
      |    "/OWN_DEVICES_carlos.sanchez@ubirch.com"
      |  ],
      |  "name": "Carlos Sanchez",
      |  "preferred_username": "carlos.sanchez@ubirch.com",
      |  "given_name": "Carlos",
      |  "family_name": "Sanchez",
      |  "email": "carlos.sanchez@ubirch.com"
      |}""".stripMargin

  val user: String =
    """
      |{
      |  "exp": 1718338181,
      |  "iat": 1604336381,
      |  "jti": "2fb1c61d-2113-4b8e-9432-97c28c697b98",
      |  "iss": "https://id.dev.ubirch.com/auth/realms/ubirch-default-realm",
      |  "aud": "account",
      |  "sub": "963995ed-ce12-4ea5-89dc-b181701d1d7b",
      |  "typ": "Bearer",
      |  "azp": "ubirch-2.0-user-access",
      |  "session_state": "f334122a-4693-4826-a2c0-546391886eda",
      |  "acr": "1",
      |  "allowed-origins": [
      |    "http://localhost:9101",
      |    "https://console.dev.ubirch.com"
      |  ],
      |  "realm_access": {
      |    "roles": [
      |      "offline_access",
      |      "uma_authorization",
      |      "USER",
      |      "certification-vaccination",
      |      "vaccination-center-altoetting"
      |    ]
      |  },
      |  "resource_access": {
      |    "account": {
      |      "roles": [
      |        "manage-account",
      |        "manage-account-links",
      |        "view-profile"
      |      ]
      |    }
      |  },
      |  "scope": "fav_color profile email",
      |  "email_verified": true,
      |  "fav_fruit": [
      |    "/OWN_DEVICES_carlos.sanchez@ubirch.com"
      |  ],
      |  "name": "Carlos Sanchez",
      |  "preferred_username": "carlos.sanchez@ubirch.com",
      |  "given_name": "Carlos",
      |  "family_name": "Sanchez",
      |  "email": "carlos.sanchez@ubirch.com"
      |}""".stripMargin

  val userNoPrincipal: String =
    """
      |{
      |  "exp": 1718338181,
      |  "iat": 1604336381,
      |  "jti": "2fb1c61d-2113-4b8e-9432-97c28c697b98",
      |  "iss": "https://id.dev.ubirch.com/auth/realms/ubirch-default-realm",
      |  "aud": "account",
      |  "sub": "963995ed-ce12-4ea5-89dc-b181701d1d7b",
      |  "typ": "Bearer",
      |  "azp": "ubirch-2.0-user-access",
      |  "session_state": "f334122a-4693-4826-a2c0-546391886eda",
      |  "acr": "1",
      |  "allowed-origins": [
      |    "http://localhost:9101",
      |    "https://console.dev.ubirch.com"
      |  ],
      |  "realm_access": {
      |    "roles": [
      |      "offline_access",
      |      "uma_authorization",
      |      "USER",
      |      "vaccination-center-altoetting",
      |      "vaccination-center-berlin"
      |    ]
      |  },
      |  "resource_access": {
      |    "account": {
      |      "roles": [
      |        "manage-account",
      |        "manage-account-links",
      |        "view-profile"
      |      ]
      |    }
      |  },
      |  "scope": "fav_color profile email",
      |  "email_verified": true,
      |  "fav_fruit": [
      |    "/OWN_DEVICES_carlos.sanchez@ubirch.com"
      |  ],
      |  "name": "Carlos Sanchez",
      |  "preferred_username": "carlos.sanchez@ubirch.com",
      |  "given_name": "Carlos",
      |  "family_name": "Sanchez",
      |  "email": "carlos.sanchez@ubirch.com"
      |}""".stripMargin

  val userWithDoubleRoles: String =
    """
      |{
      |  "exp": 1718338181,
      |  "iat": 1604336381,
      |  "jti": "2fb1c61d-2113-4b8e-9432-97c28c697b98",
      |  "iss": "https://id.dev.ubirch.com/auth/realms/ubirch-default-realm",
      |  "aud": "account",
      |  "sub": "963995ed-ce12-4ea5-89dc-b181701d1d7b",
      |  "typ": "Bearer",
      |  "azp": "ubirch-2.0-user-access",
      |  "session_state": "f334122a-4693-4826-a2c0-546391886eda",
      |  "acr": "1",
      |  "allowed-origins": [
      |    "http://localhost:9101",
      |    "https://console.dev.ubirch.com"
      |  ],
      |  "realm_access": {
      |    "roles": [
      |      "offline_access",
      |      "uma_authorization",
      |      "USER",
      |      "certification-vaccination",
      |      "vaccination-center-altoetting",
      |      "vaccination-center-berlin"
      |    ]
      |  },
      |  "resource_access": {
      |    "account": {
      |      "roles": [
      |        "manage-account",
      |        "manage-account-links",
      |        "view-profile"
      |      ]
      |    }
      |  },
      |  "scope": "fav_color profile email",
      |  "email_verified": true,
      |  "fav_fruit": [
      |    "/OWN_DEVICES_carlos.sanchez@ubirch.com"
      |  ],
      |  "name": "Carlos Sanchez",
      |  "preferred_username": "carlos.sanchez@ubirch.com",
      |  "given_name": "Carlos",
      |  "family_name": "Sanchez",
      |  "email": "carlos.sanchez@ubirch.com"
      |}""".stripMargin

}

@Singleton
class FakeTokenCreator @Inject() (privKey: PrivKey, tokenCreationService: TokenCreationService) {

  def fakeToken(header: String, token: String): FakeToken = {
    FakeToken(
      tokenCreationService
        .encode(header, token, privKey)
        .getOrElse(throw new Exception("Error Creating Token"))
    )
  }

  val user: FakeToken = fakeToken(FakeToken.header, FakeToken.user)
  val userWithDoubleRoles: FakeToken = fakeToken(FakeToken.header, FakeToken.userWithDoubleRoles)
  val userNoPrincipal: FakeToken = fakeToken(FakeToken.header, FakeToken.userNoPrincipal)
  val admin: FakeToken = fakeToken(FakeToken.header, FakeToken.admin)

}

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

class PostgresInjectorHelperImpl(val postgreContainer: PostgresContainer)
  extends InjectorHelper(List(new Binder {
    override def PublicKeyPoolService: ScopedBindingBuilder = {
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])
    }

    override def QuillJdbcContext: ScopedBindingBuilder =
      bind(classOf[QuillJdbcContext]).toConstructor(
        classOf[TestPostgresQuillJdbcContext].getConstructor(classOf[PostgresRuntimeConfig]))

    override def configure(): Unit = {
      bind(classOf[PostgresRuntimeConfig]).toInstance(
        PostgresRuntimeConfig(
          postgreContainer.container.getContainerIpAddress,
          postgreContainer.container.getFirstMappedPort))
      bind(classOf[PrivKey]).toProvider(classOf[KeyPairProvider])
      super.configure()
    }
  }))

class TestKeycloakInjectorHelperImpl(val keycloakContainer: KeycloakContainer, val clientAdmin: ClientAdmin)
  extends InjectorHelper(List(new Binder {

    override def PublicKeyPoolService: ScopedBindingBuilder = {
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])
    }

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
      bind(classOf[KeycloakRuntimeConfig]).toInstance(
        KeycloakRuntimeConfig(
          keycloakContainer.container.getContainerIpAddress,
          keycloakContainer.container.getFirstMappedPort,
          clientAdmin))
      bind(classOf[PrivKey]).toProvider(classOf[KeyPairProvider])
      super.configure()
    }
  }))
