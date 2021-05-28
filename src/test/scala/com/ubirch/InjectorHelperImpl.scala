package com.ubirch

import com.typesafe.config.Config
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{ GeneratorKeyFactory, PrivKey }
import com.ubirch.models.tenant.TenantName
import com.ubirch.services.jwt.{ DefaultPublicKeyPoolService, PublicKeyDiscoveryService, TokenCreationService }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.eval.Task

import java.security.Key
import java.util.UUID
import javax.inject.{ Inject, Singleton }

@Singleton
class FakeDefaultPublicKeyPoolService @Inject() (config: Config, publicKeyDiscoveryService: PublicKeyDiscoveryService)
  extends DefaultPublicKeyPoolService(config, publicKeyDiscoveryService) {

  override def getKeyFromDiscoveryService(keycloakInstance: KeycloakInstance, kid: String): Task[Option[Key]] =
    keycloakInstance match {
      case CertifyKeycloak =>
        Task {
          kid match {
            case "6dMHOUfu7v6howP2WH5bkp-j9UgUYdyEQbWJp8cb8IY" => Some(CertifyKeyPairProvider.privKey.getPublicKey)
            case _                                             => throw new Exception("Check certify keycloak kid!")
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

object CertifyKeyPairProvider extends {
  val privKey: PrivKey = GeneratorKeyFactory.getPrivKey(Curve.PRIME256V1)
}

object DeviceKeyPairProvider extends {
  val privKey: PrivKey = GeneratorKeyFactory.getPrivKey(Curve.PRIME256V1)
}

case class FakeToken(value: String) {
  def prepare: String = "bearer " + value
}

object FakeToken {

  val certifyHeader: String =
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

  def tenantAdmin(tenantName: TenantName): String =
    s"""
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
       |      "$TENANT_GROUP_PREFIX${tenantName.value}",
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

  def pocAdmin(id: UUID): String =
    s"""
       |{
       |  "exp": 1718338181,
       |  "iat": 1604336381,
       |  "jti": "2fb1c61d-2113-4b8e-9432-97c28c697b98",
       |  "iss": "https://id.dev.ubirch.com/auth/realms/ubirch-default-realm",
       |  "aud": "account",
       |  "sub": "${id.toString}",
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
       |      "poc-admin"
       |    ]
       |  },
       |  "resource_access": {
       |    "account": {
       |      "roles": [
       |        "poc-admin"
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

  def pocEmployee(id: UUID): String =
    s"""
       |{
       |  "exp": 1718338181,
       |  "iat": 1604336381,
       |  "jti": "2fb1c61d-2113-4b8e-9432-97c28c697b98",
       |  "iss": "https://id.dev.ubirch.com/auth/realms/ubirch-default-realm",
       |  "aud": "account",
       |  "sub": "${id.toString}",
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
       |      "poc-employee"
       |    ]
       |  },
       |  "resource_access": {
       |    "account": {
       |      "roles": [
       |        "poc-employee"
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
      case CertifyKeycloak => CertifyKeyPairProvider.privKey
      case DeviceKeycloak  => DeviceKeyPairProvider.privKey
    }
    FakeToken(
      tokenCreationService
        .encode(header, token, privKey)
        .getOrElse(throw new Exception("Error Creating Token"))
    )
  }

  val user: FakeToken = fakeToken(FakeToken.certifyHeader, FakeToken.user, CertifyKeycloak)
  def userOnDevicesKeycloak(tenantName: TenantName): FakeToken =
    fakeToken(FakeToken.certifyHeader, FakeToken.tenantAdmin(tenantName), CertifyKeycloak)
  val superAdmin: FakeToken = fakeToken(FakeToken.certifyHeader, FakeToken.superAdmin, CertifyKeycloak)
  val superAdminOnDevicesKeycloak: FakeToken = fakeToken(FakeToken.deviceHeader, FakeToken.superAdmin, DeviceKeycloak)
  val userWithDoubleRoles: FakeToken =
    fakeToken(FakeToken.certifyHeader, FakeToken.userWithDoubleRoles, CertifyKeycloak)
  def pocAdmin(certifyId: UUID) = fakeToken(FakeToken.certifyHeader, FakeToken.pocAdmin(certifyId), CertifyKeycloak)
  def pocEmployee(certifyId: UUID) =
    fakeToken(FakeToken.certifyHeader, FakeToken.pocEmployee(certifyId), CertifyKeycloak)
  val userNoPrincipal: FakeToken = fakeToken(FakeToken.certifyHeader, FakeToken.userNoPrincipal, CertifyKeycloak)
  val admin: FakeToken = fakeToken(FakeToken.certifyHeader, FakeToken.admin, CertifyKeycloak)

}
