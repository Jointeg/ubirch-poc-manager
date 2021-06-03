package com.ubirch.e2e

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigFactory }
import com.ubirch._
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.services.clock.ClockProvider
import com.ubirch.formats.TestFormats
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.users.{ KeycloakUserService, KeycloakUserServiceWithoutMail }
import com.ubirch.services.keycloak.{ DeviceKeycloakConnector, KeycloakCertifyConfig, KeycloakDeviceConfig }
import com.ubirch.services.poc._
import com.ubirch.services.teamdrive.model.TeamDriveClient
import com.ubirch.test.{ FakeTeamDriveClient, FixedClockProvider }
import io.getquill.{ PostgresJdbcContext, SnakeCase }
import com.ubirch.test.FakeTeamDriveClient
import io.getquill.context.monix.MonixJdbcContext.Runner
import io.getquill.{ PostgresMonixJdbcContext, SnakeCase }
import monix.eval.Task
import org.json4s.native.Serialization
import org.json4s.{ DefaultFormats, Formats }
import org.keycloak.representations.idm.GroupRepresentation
import sttp.client._
import sttp.client.json4s._
import sttp.client.quick.backend

import java.time.Clock
import javax.inject.{ Inject, Singleton }
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

case class RuntimeGroupMaps(dataSchemaGroupMap: Map[String, String], trustedPocGroupMap: Map[String, String])
case class KeycloakUsersRuntimeConfig(tenantAdmin: TenantAdmin)
case class KeyResponse(kid: String, alg: String)
case class KeycloakKidResponse(keys: List[KeyResponse])

@Singleton
class TestPocConfig @Inject() (
  val conf: Config,
  keycloakConnector: DeviceKeycloakConnector)
  extends PocConfigImpl(conf) {

  val groupRepresentation = new GroupRepresentation()
  groupRepresentation.setName("vaccination-v3")
  keycloakConnector.keycloak.realm("device-realm").groups().add(groupRepresentation)
  val id = keycloakConnector.keycloak.realm("device-realm").groups().groups().asScala.head
  override val dataSchemaGroupMap = Map("vaccination-v3" -> id.getId)
  override val trustedPocGroupMap: Map[String, String] = Map("vaccination-v3" -> id.getId)
}

@Singleton
class TestKeycloakCertifyConfig @Inject() (val conf: Config, keycloakRuntimeConfig: KeycloakUsersRuntimeConfig)
  extends KeycloakCertifyConfig {

  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  implicit private val formats: Formats = DefaultFormats.lossless ++ TestFormats.all

  private val keycloakServer = KeycloakCertifyContainer.container.container.getContainerIpAddress
  private val keycloakPort = KeycloakCertifyContainer.container.container.getFirstMappedPort

  private lazy val kid = {
    basicRequest
      .get(uri"http://$keycloakServer:$keycloakPort/auth/realms/certify-realm/protocol/openid-connect/certs")
      .response(asJson[KeycloakKidResponse])
      .send()
  }

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
  val configUrl: String =
    s"http://$keycloakServer:$keycloakPort/auth/realms/certify-realm/.well-known/openid-configuration"
  lazy val acceptedKid: String = kid.body
    .right
    .get
    .keys
    .find(_.alg == "ES256")
    .get.kid
}

@Singleton
class TestKeycloakDeviceConfig @Inject() (val conf: Config) extends KeycloakDeviceConfig {

  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  implicit private val formats: Formats = DefaultFormats.lossless ++ TestFormats.all

  private val keycloakServer = KeycloakDeviceContainer.container.container.getContainerIpAddress
  private val keycloakPort = KeycloakDeviceContainer.container.container.getFirstMappedPort

  private lazy val kid = {
    basicRequest
      .get(uri"http://$keycloakServer:$keycloakPort/auth/realms/device-realm/protocol/openid-connect/certs")
      .response(asJson[KeycloakKidResponse])
      .send()
  }

  val serverUrl: String = s"http://$keycloakServer:$keycloakPort/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.CLIENT_ID)
  val realm: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.REALM)
  val configUrl: String =
    s"http://$keycloakServer:$keycloakPort/auth/realms/device-realm/.well-known/openid-configuration"
  lazy val acceptedKid: String = kid.body
    .right
    .get
    .keys
    .find(_.alg == "ES256")
    .get.kid
}

@Singleton
class TestPostgresQuillMonixJdbcContext @Inject() (clock: Clock) extends QuillMonixJdbcContext {
  override val ctx: PostgresMonixJdbcContext[SnakeCase] = StaticTestPostgresJdbcContext.ctx

  override def withTransaction[T](f: => Task[T]): Task[T] = f

  override val systemClock: Clock = clock
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

class E2EInjectorHelperImpl(
  val superAdmin: SuperAdmin,
  val tenantAdmin: TenantAdmin,
  val discoveryServiceType: DiscoveryServiceType)
  extends InjectorHelper(List(new Binder {

    override def PublicKeyPoolService: ScopedBindingBuilder = {
      bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])
    }

    override def QuillMonixJdbcContext: ScopedBindingBuilder =
      bind(classOf[QuillMonixJdbcContext]).to(classOf[TestPostgresQuillMonixJdbcContext])

    override def DeviceCreator: ScopedBindingBuilder =
      bind(classOf[DeviceCreator]).to(classOf[DeviceCreatorMockSuccess])

    override def PocConfig: ScopedBindingBuilder =
      bind(classOf[PocConfig]).to(classOf[TestPocConfig])

    override def InformationProvider: ScopedBindingBuilder =
      bind(classOf[InformationProvider]).to(classOf[InformationProviderMockSuccess])

    override def KeycloakUsersConfig: ScopedBindingBuilder = {
      bind(classOf[KeycloakCertifyConfig]).toConstructor(
        classOf[TestKeycloakCertifyConfig].getConstructor(classOf[Config], classOf[KeycloakUsersRuntimeConfig])
      )
    }

    override def KeycloakUserService: ScopedBindingBuilder = {
      bind(classOf[KeycloakUserService]).to(classOf[KeycloakUserServiceWithoutMail])
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
      bind(classOf[DiscoveryServiceType]).toInstance(discoveryServiceType)
    }

    override def TeamDriveClient: ScopedBindingBuilder =
      bind(classOf[TeamDriveClient]).to(classOf[FakeTeamDriveClient])

    override def Clock: ScopedBindingBuilder = bind(classOf[Clock]).toProvider(classOf[FixedClockProvider])
  }))
