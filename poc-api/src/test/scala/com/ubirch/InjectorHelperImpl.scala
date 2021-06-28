package com.ubirch

import com.ubirch.controllers.concerns.HeaderKeys
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{ GeneratorKeyFactory, PrivKey }
import com.ubirch.e2e.DiscoveryServiceType
import com.ubirch.models.tenant.TenantName
import com.ubirch.services.jwt.{ DefaultPublicKeyPoolService, PublicKeyDiscoveryService, TokenCreationService }
import com.ubirch.services.keycloak.{ KeycloakCertifyConfig, KeycloakDeviceConfig }
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.eval.Task

import java.security.Key
import java.util.UUID
import javax.inject.{ Inject, Singleton }

@Singleton
class FakeDefaultPublicKeyPoolService @Inject() (
  keycloakDeviceConfig: KeycloakDeviceConfig,
  keycloakCertifyConfig: KeycloakCertifyConfig,
  publicKeyDiscoveryService: PublicKeyDiscoveryService,
  useMockKeyDiscoveryService: DiscoveryServiceType)
  extends DefaultPublicKeyPoolService(keycloakCertifyConfig, keycloakDeviceConfig, publicKeyDiscoveryService) {

  override protected def acceptedKids(keycloakInstance: KeycloakInstance): List[String] = {
    if (useMockKeyDiscoveryService.isMock) {
      keycloakInstance match {
        case CertifyKeycloak => List("6dMHOUfu7v6howP2WH5bkp-j9UgUYdyEQbWJp8cb8IY")
        case DeviceKeycloak  => List("tgxjDoFtQP7tzzO6byck4X8vsFRaM5EVz0N66O9CSTg")
      }
    } else {
      keycloakInstance match {
        case CertifyKeycloak => List(keycloakCertifyConfig.acceptedKid)
        case DeviceKeycloak  => List(keycloakDeviceConfig.acceptedKid)
      }
    }
  }

  override def getKeyFromDiscoveryService(keycloakInstance: KeycloakInstance, kid: String): Task[Option[Key]] =
    keycloakInstance match {
      case CertifyKeycloak =>
        kid match {
          case "6dMHOUfu7v6howP2WH5bkp-j9UgUYdyEQbWJp8cb8IY" => Task(Some(CertifyKeyPairProvider.privKey.getPublicKey))
          case _                                             => super.getKeyFromDiscoveryService(keycloakInstance, kid)
        }
      case DeviceKeycloak =>
        kid match {
          case "tgxjDoFtQP7tzzO6byck4X8vsFRaM5EVz0N66O9CSTg" => Task(Some(DeviceKeyPairProvider.privKey.getPublicKey))
          case _                                             => super.getKeyFromDiscoveryService(keycloakInstance, kid)
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
  def pocAdmin(certifyId: UUID): FakeToken =
    fakeToken(FakeToken.certifyHeader, FakeToken.pocAdmin(certifyId), CertifyKeycloak)
  def pocEmployee(certifyId: UUID): FakeToken =
    fakeToken(FakeToken.certifyHeader, FakeToken.pocEmployee(certifyId), CertifyKeycloak)
  val userNoPrincipal: FakeToken = fakeToken(FakeToken.certifyHeader, FakeToken.userNoPrincipal, CertifyKeycloak)

}

object FakeX509Certs {
  val client =
    "MIIDjjCCAnagAwIBAgIJAKY0mtzrX%2BotMA0GCSqGSIb3DQEBCwUAMEcxCzAJBgNV%0D%0ABAYTAkRFMRAwDgYDVQQIDAdDb2xvZ25lMRQwEgYDVQQKDAt1YmlyY2ggR21iSDEQ%0D%0AMA4GA1UEAwwHSXNzdWluZzAgFw0yMTA2MDkxMzU1MTRaGA8yMTIxMDUxNjEzNTUx%0D%0ANFowRjELMAkGA1UEBhMCREUxEDAOBgNVBAgMB0NvbG9nbmUxFDASBgNVBAoMC3Vi%0D%0AaXJjaCBHbWJIMQ8wDQYDVQQDDAZDbGllbnQwggEiMA0GCSqGSIb3DQEBAQUAA4IB%0D%0ADwAwggEKAoIBAQC4xjQoCmAf4ZApLazvuohQA0aN0oKPpLxGyCW7QztYUJ9w%2Bggs%0D%0AyDJFX%2B86iOGAgkRrwl4GPq0njN%2BWltP10k0cl88mC2Z6hmgl1wo06Lg6BQbEjNI0%0D%0AgM7lu9QEKdhlJeT5NPKSI7%2FHmrSYEK8j0%2BCzSiyBLn%2FfkC9fRfSPdZO%2BU8t1GlNs%0D%0AmVl7sDpKQRGz0BmbtaDCNGGzCozrdFCx30aR0npFLDf0LxYQ3jkMDsgVv1uAmekX%0D%0AS7bENioQjEpqZGgyc%2FiZ9EaPTjJIlzqcZWoddBBmnTuvM1Ik8bLc9ukQMl9a5Uuu%0D%0AWwatbQrayp5rruiGgjnhqmKi8%2FHXzhXim6NRAgMBAAGjfDB6MAwGA1UdEwEB%2FwQC%0D%0AMAAwCwYDVR0PBAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAd%0D%0ABgNVHQ4EFgQULf3132pXWtG0RrCs4ImmfnJ%2B2cEwHwYDVR0jBBgwFoAUfhoS1LQT%0D%0AqNxNwpDMDxPuLRPzM4UwDQYJKoZIhvcNAQELBQADggEBAKFkZ4Z%2Bfe1uMWVIXZmo%0D%0ACydMNhej%2FAG4BnBTYHhXhhT1JCQUeNbXhk9O4rmcBUHE2SfIgUesOuIbAfzyDI9o%0D%0Akwwu%2B0TzRBDIk7woqVP3EUqCWrUoiPW356t%2BM5675EU%2FmR7CjPWll4VywJGr4GNl%0D%0AZZFDjrsQc2lMJtZBCKNMhB0b07bwaewXzcB4EHJqOqGpNC5IgMdGkAs6aI%2B2ivma%0D%0AMfQKBpAhAgEgcwD9Z%2B2c7lOTdiB4OgyM6WME4FmtKQZBjDovuK8HXzMBshu%2BWCZg%0D%0AcK2KF6bGCLQ535TkTeVqjcJV9dqV%2BXVCTFmhcuS2WSwg%2Bh08wy%2FQF1ufGDyQpGqz%0D%0A2DE%3D"
  val issuing =
    "MIIDlDCCAnygAwIBAgIJAJB6q3lyhdnCMA0GCSqGSIb3DQEBCwUAMEwxCzAJBgNV%0D%0ABAYTAkRFMRAwDgYDVQQIDAdDb2xvZ25lMRQwEgYDVQQKDAt1YmlyY2ggR21iSDEV%0D%0AMBMGA1UEAwwMSW50ZXJtZWRpYXRlMCAXDTIxMDYwOTEzNTQ0NVoYDzIxMjEwNTE2%0D%0AMTM1NDQ1WjBHMQswCQYDVQQGEwJERTEQMA4GA1UECAwHQ29sb2duZTEUMBIGA1UE%0D%0ACgwLdWJpcmNoIEdtYkgxEDAOBgNVBAMMB0lzc3VpbmcwggEiMA0GCSqGSIb3DQEB%0D%0AAQUAA4IBDwAwggEKAoIBAQDhrRcDbjd4pSK2CXF8Zqzez28cmpAJU%2Fg7O8CsJeiS%0D%0AKCBMsTvPgbh6q2S2nT9XD98dycczkvbH4NpxuKUawU9MMSZVNCTqxwF9qhULfMcc%0D%0ATYw%2FmKE2DjM6QFUeF1lyXM98oGu4PhdbdUWdrkeiXYwBWZ7vdRC9qsA%2FmcSJ14Cm%0D%0AW05AXQ5%2FzW4UpdJu15TdpHFfOsPPfno3vHTXYa%2BmYhgyXm1Cm9KVAuNnjOcvNnBM%0D%0ANj5iml1G1ZnveKKNWx3fII1%2F%2BUxoxJhsVkYKBiTpDXb0fU37yBGyF25p3Bq5gCPT%0D%0Aa0JqdATujg3Rd%2BsWkud8lKsojkkEeT5EsISlQnVczn3JAgMBAAGjfDB6MAwGA1Ud%0D%0AEwEB%2FwQCMAAwCwYDVR0PBAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF%0D%0ABQcDAjAdBgNVHQ4EFgQUfhoS1LQTqNxNwpDMDxPuLRPzM4UwHwYDVR0jBBgwFoAU%0D%0AK%2FKC%2BPI97mF57Oe0Ye02lk%2B%2FnB8wDQYJKoZIhvcNAQELBQADggEBAKpGnQZp9kr0%0D%0A26r03pE59sIt8nzLsysgdD85ImyHQZZIJjnK8HUs5p1JFqKXYuvIOgYEWOfM9m39%0D%0AbZ9dJsgXjqB33mVq7WVQjdo4MsJvKsn0qXQJQURKZ7fAEFVshNO6LvbEG6ZfeCLJ%0D%0AqDoarByp8lxi%2Fxhpc%2F%2BaduZAUpB7AjJBXhrJYGl4xS5iYvLA%2BPupNTfhU9Djz3tz%0D%0AZcndmZDkt8vcnoHpKw29YsSNCg2zrHNWext6hhDWDF4chUfV%2B6K3p3%2Bft3F4jueJ%0D%0AufJmbf0FM4EHC513Ev7xCj4rXTlbfGMIpBW3WwrAENffgkMdz03r9idRy1ffSAzg%0D%0AI3mwX7Dxhvc%3D"
  val intermediate =
    "MIIDlDCCAnygAwIBAgIJAI0n2KVDlDe0MA0GCSqGSIb3DQEBCwUAMEcxCzAJBgNV%0D%0ABAYTAkRFMRAwDgYDVQQIDAdDb2xvZ25lMRQwEgYDVQQKDAt1YmlyY2ggR21iSDEQ%0D%0AMA4GA1UEAwwHVGVzdCBDQTAgFw0yMTA2MDkxMzU0MjdaGA8yMTIxMDUxNjEzNTQy%0D%0AN1owTDELMAkGA1UEBhMCREUxEDAOBgNVBAgMB0NvbG9nbmUxFDASBgNVBAoMC3Vi%0D%0AaXJjaCBHbWJIMRUwEwYDVQQDDAxJbnRlcm1lZGlhdGUwggEiMA0GCSqGSIb3DQEB%0D%0AAQUAA4IBDwAwggEKAoIBAQC40ZaY7IREHs4zI7YpvFeoRRzxwqeSRICaYUIpwb%2BL%0D%0ANxpuyE%2F0ygwZV%2Brvt%2FTCzkcXFDXQ%2FVmimSeXzWUy8E7hDyFhIzgTiuiGPF2l39IX%0D%0AcjYvm%2FU0mu9Ipb%2BbMrEtacXGnF8qienP5alvZuAPOYCNF6gO704hWNcnvBHPG7OW%0D%0Av%2FRXrpoz8KNmsbdlMJx1%2Bf%2FukMb8fTj4SuyMKnWgpXLbTPxA32yoyR%2FFYmnCLETq%0D%0AxGZsSxKsjjkcsPje21chJU5NEAhw900je%2B8g0yCD5R7wqps2QLtMtjGi%2Fv8b%2BO79%0D%0AYmpLKWlQewCV1VJq23nLzarApHamFhVq1469QAVY%2FdH3AgMBAAGjfDB6MAwGA1Ud%0D%0AEwEB%2FwQCMAAwCwYDVR0PBAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEF%0D%0ABQcDAjAdBgNVHQ4EFgQUK%2FKC%2BPI97mF57Oe0Ye02lk%2B%2FnB8wHwYDVR0jBBgwFoAU%0D%0A1Um2u6oCBoFthpLIqEkfSfmFaQIwDQYJKoZIhvcNAQELBQADggEBAJGiNszmyEkC%0D%0AHc7fPJIFcMIU9QLgHj1lUcz6%2FyLVmMAg%2F1dz7riX8uvmyH%2BOQ6wJmJAfjrTLcSCg%0D%0AnwcNsWwOOSNZqmd%2Bz9ZO5wvjC4eEg9nPVMOS%2B2QDbNXZ4vSShIxczICesm7l79B%2B%0D%0A3dBwHNwd9102cZXucR6yXNckqPR6KbUfSgub%2Ffad8tsfPRi9feOh2t%2BfRNXeU2%2FC%0D%0ARPg4jl9pUCNCVQnlCI0Z85C1PgbyQHTALzSCTtkZwJFnZYRQdpyY3A4ZwL9e0x0P%0D%0ASbiEsDkquO1cB0ua1%2BZ2DCQqM%2B%2BxTrWqdWHybJnBBShBHYHmxT%2BxTCrx9jAkhouH%0D%0AGIAmq9CXzgY%3D"
  val root =
    "MIIDkjCCAnqgAwIBAgIJAIP7v3WvGKgHMA0GCSqGSIb3DQEBCwUAMEcxCzAJBgNV%0D%0ABAYTAkRFMRAwDgYDVQQIDAdDb2xvZ25lMRQwEgYDVQQKDAt1YmlyY2ggR21iSDEQ%0D%0AMA4GA1UEAwwHVGVzdCBDQTAgFw0yMTA2MDkxMzUyMDFaGA8yMTIxMDUxNjEzNTIw%0D%0AMVowRzELMAkGA1UEBhMCREUxEDAOBgNVBAgMB0NvbG9nbmUxFDASBgNVBAoMC3Vi%0D%0AaXJjaCBHbWJIMRAwDgYDVQQDDAdUZXN0IENBMIIBIjANBgkqhkiG9w0BAQEFAAOC%0D%0AAQ8AMIIBCgKCAQEAyQSXklBB4%2ByHDwJNQpxQCwXaAH03Cq1XQPVIpMdUuVkByDxn%0D%0ATEPu0jQ9vtZODpO8NDaCUL9FGzstaK19jXnS%2B%2BOKBq2emaLXTmGrSnTb46i6bkib%0D%0ABKklQoHjSGM9NJXzkQd6J5bK7Gg%2BPni%2BnXxUy6dvFPIR2QSuIOGLBlojwW%2BFvKPf%0D%0A0Ly3NRr3eRWTVtNmg%2BKiOsDQbJKFJdtF58saQgrsrkIX4T0BJYghWf2glrkqHICw%0D%0A4JR%2FN0JKJS4BoPuw1SL8edKzfEr4cKtW7Kzi718oY4guSDf7xw4NzxumvC7NtjS%2B%0D%0ADYPy570H0s9RE1EPfoxOF2V7kSwCciY730rwpwIDAQABo38wfTAPBgNVHRMBAf8E%0D%0ABTADAQH%2FMAsGA1UdDwQEAwIBBjAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUH%0D%0AAwIwHQYDVR0OBBYEFNVJtruqAgaBbYaSyKhJH0n5hWkCMB8GA1UdIwQYMBaAFNVJ%0D%0AtruqAgaBbYaSyKhJH0n5hWkCMA0GCSqGSIb3DQEBCwUAA4IBAQBiJr4e5wHcSw7w%0D%0A%2FlkTHVJeHBdjNrwnXg7JWZa%2FUSikKij%2B8WAlThKmUCP1SvQb4h%2FukapJ0dQFOyER%0D%0ANNzQ9dkJsGb93xEOLLaM6kwoYjgMndkm3Q2VX5iO8eeme4eVbCqmmAqP4Dan1y1t%0D%0ABC2aDJQCAyugTl3oDAc35UitJFMQD5HSngNNd4sHsCLTZhN7pWGjbGe76SFC%2Fszl%0D%0AIwIVwRgWvZrUCMg%2F8bYoFLsHyIn5fDyvI1iWaSNtUOzhXoAbb6VK%2F6hMvygrcoPq%0D%0AGAz%2BVu1GfovwGiVEbAsykEDLkNP2ewHWuDECJLcAHHjslVX7S14q4Wesacn1Xi1O%0D%0AwnDlBHO%2F"

  val validX509Header = HeaderKeys.TLS_HEADER_KEY -> Seq(client, issuing, intermediate, root).mkString(",")
  val invalidSingleX509Header = HeaderKeys.TLS_HEADER_KEY -> client
  val x509HeaderUntilIssuer = HeaderKeys.TLS_HEADER_KEY -> Seq(client, issuing).mkString(",")
  val x509HeaderWithWrongOrder = HeaderKeys.TLS_HEADER_KEY -> Seq(client, root, intermediate, issuing).mkString(",")
  val invalidX509Header = HeaderKeys.TLS_HEADER_KEY -> Seq(client, root).mkString(",")
}
