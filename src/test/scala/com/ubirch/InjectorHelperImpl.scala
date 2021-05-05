package com.ubirch

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.Config
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{ GeneratorKeyFactory, PrivKey }
import com.ubirch.db.tables.{
  PocRepository,
  PocTestTable,
  TenantRepository,
  TenantTestTable,
  UserRepository,
  UserTestTable
}
import com.ubirch.services.jwt.{
  DefaultPublicKeyPoolService,
  PublicKeyDiscoveryService,
  PublicKeyPoolService,
  TokenCreationService
}
import com.ubirch.services.keycloak.auth.{ AuthClient, TestAuthClient }
import com.ubirch.services.keycloak.groups.{ KeycloakGroupService, TestKeycloakGroupsService }
import com.ubirch.services.keycloak.roles.{ KeycloakRolesService, TestKeycloakRolesService }
import com.ubirch.services.keycloak.users.{
  KeycloakUserService,
  TestKeycloakUserService,
  TestUserPollingService,
  UserPollingService
}
import com.ubirch.services.{ DeviceKeycloak, KeycloakInstance, UsersKeycloak }
import monix.eval.Task

import java.security.Key
import javax.inject.{ Inject, Singleton }

@Singleton
class FakeDefaultPublicKeyPoolService @Inject() (config: Config, publicKeyDiscoveryService: PublicKeyDiscoveryService)
  extends DefaultPublicKeyPoolService(config, publicKeyDiscoveryService) {

  override def getKeyFromDiscoveryService(keycloakInstance: KeycloakInstance, kid: String): Task[Option[Key]] =
    keycloakInstance match {
      case UsersKeycloak =>
        Task {
          kid match {
            case "6dMHOUfu7v6howP2WH5bkp-j9UgUYdyEQbWJp8cb8IY" => Some(UsersKeyPairProvider.privKey.getPublicKey)
            case _                                             => throw new Exception("Check users keycloak kid!")
          }
        }
      case DeviceKeycloak =>
        Task {
          kid match {
            case "tgxjDoFtQP7tzzO6byck4X8vsFRaM5EVz0N66O9CSTg" => Some(DeviceKeyPairProvider.privKey.getPublicKey)
            case _                                             => throw new Exception("Check device keycloak kid!")
          }
        }
    }
}

object UsersKeyPairProvider extends {
  val privKey: PrivKey = GeneratorKeyFactory.getPrivKey(Curve.PRIME256V1)
}

object DeviceKeyPairProvider extends {
  val privKey: PrivKey = GeneratorKeyFactory.getPrivKey(Curve.PRIME256V1)
}

case class FakeToken(value: String) {
  def prepare: String = "bearer " + value
}

object FakeToken {

  val usersHeader: String =
    """
      |{
      |  "alg": "ES256",
      |  "typ": "JWT",
      |  "kid": "6dMHOUfu7v6howP2WH5bkp-j9UgUYdyEQbWJp8cb8IY"
      |}""".stripMargin

  val deviceHeader: String =
    """
      |  "alg": "ES256",
      |  "typ": "JWT",
      |  "kid": "tgxjDoFtQP7tzzO6byck4X8vsFRaM5EVz0N66O9CSTg"
      |""".stripMargin

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

  val tenantAdmin: String =
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
      |      "tenant-admin",
      |      "TEN_tenantName",
      |    ]
      |  },
      |  "resource_access": {
      |    "account": {
      |      "roles": [
      |        "manage-account",
      |        "manage-account-links",
      |        "view-profile",
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

  val superAdmin: String =
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
      |      "super-admin"
      |    ]
      |  },
      |  "resource_access": {
      |    "account": {
      |      "roles": [
      |        "manage-account",
      |        "manage-account-links",
      |        "view-profile",
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
      |      "vaccination-center-altoetting",
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
class FakeTokenCreator @Inject() (tokenCreationService: TokenCreationService) {

  def fakeToken(header: String, token: String, keycloakInstance: KeycloakInstance): FakeToken = {
    val privKey = keycloakInstance match {
      case UsersKeycloak  => UsersKeyPairProvider.privKey
      case DeviceKeycloak => DeviceKeyPairProvider.privKey
    }
    FakeToken(
      tokenCreationService
        .encode(header, token, privKey)
        .getOrElse(throw new Exception("Error Creating Token"))
    )
  }

  val user: FakeToken = fakeToken(FakeToken.usersHeader, FakeToken.user, UsersKeycloak)
  val userOnDevicesKeycloak: FakeToken = fakeToken(FakeToken.deviceHeader, FakeToken.tenantAdmin, DeviceKeycloak)
  val superAdmin: FakeToken = fakeToken(FakeToken.deviceHeader, FakeToken.superAdmin, DeviceKeycloak)
  val superAdminOnUsersKeycloak: FakeToken = fakeToken(FakeToken.usersHeader, FakeToken.superAdmin, UsersKeycloak)
  val userWithDoubleRoles: FakeToken = fakeToken(FakeToken.usersHeader, FakeToken.userWithDoubleRoles, UsersKeycloak)
  val userNoPrincipal: FakeToken = fakeToken(FakeToken.usersHeader, FakeToken.userNoPrincipal, UsersKeycloak)
  val admin: FakeToken = fakeToken(FakeToken.usersHeader, FakeToken.admin, UsersKeycloak)

}

class UnitTestInjectorHelper()
  extends InjectorHelper(List(new Binder {
    override def PublicKeyPoolService: ScopedBindingBuilder =
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])

    override def UserRepository: ScopedBindingBuilder =
      bind(classOf[UserRepository]).to(classOf[UserTestTable])

    override def PocRepository: ScopedBindingBuilder =
      bind(classOf[PocRepository]).to(classOf[PocTestTable])

    override def TenantRepository: ScopedBindingBuilder =
      bind(classOf[TenantRepository]).to(classOf[TenantTestTable])

    override def UserPollingService: ScopedBindingBuilder =
      bind(classOf[UserPollingService]).to(classOf[TestUserPollingService])

    override def AuthClient: ScopedBindingBuilder =
      bind(classOf[AuthClient]).to(classOf[TestAuthClient])

    override def KeycloakGroupService: ScopedBindingBuilder =
      bind(classOf[KeycloakGroupService]).to(classOf[TestKeycloakGroupsService])

    override def KeycloakRolesService: ScopedBindingBuilder =
      bind(classOf[KeycloakRolesService]).to(classOf[TestKeycloakRolesService])

    override def KeycloakUserService: ScopedBindingBuilder =
      bind(classOf[KeycloakUserService]).to(classOf[TestKeycloakUserService])

    override def configure(): Unit = {
      super.configure()
    }
  }))
