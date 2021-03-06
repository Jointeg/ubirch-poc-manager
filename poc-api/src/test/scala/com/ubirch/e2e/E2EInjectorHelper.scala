package com.ubirch.e2e

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch._
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.e2e.StartupHelperMethods.isPortInUse
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
  keycloakConnector
    .keycloak
    .realm("ubirch-default-realm")
    .groups()
    .add(groupRepresentation)
  val id: GroupRepresentation = keycloakConnector
    .keycloak
    .realm("ubirch-default-realm")
    .groups()
    .groups()
    .asScala
    .head
  override val dataSchemaGroupMap = Map("vaccination-v3" -> id.getId)
  override val trustedPocGroupMap: Map[String, String] = Map("vaccination-v3" -> id.getId)
}

@Singleton
class TestKeycloakCertifyConfig @Inject() (val conf: Config) extends KeycloakCertifyConfig with LazyLogging {

  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  implicit private val formats: Formats = DefaultFormats.lossless ++ TestFormats.all

  private val defaultPort = 8080
  private val isLocalDockerRunning = isPortInUse(defaultPort)

  private val keycloakServer =
    if (isLocalDockerRunning) {
      "localhost"
    } else {
      KeycloakCertifyContainer.container.container.getContainerIpAddress
    }
  private val keycloakPort =
    if (isLocalDockerRunning) {
      defaultPort
    } else {
      KeycloakCertifyContainer.container.container.getFirstMappedPort
    }

  private lazy val kid = {
    basicRequest
      .get(uri"http://$keycloakServer:$keycloakPort/auth/realms/poc-certify/protocol/openid-connect/certs")
      .response(asJson[KeycloakKidResponse])
      .send()
  }

  val serverUrl: String = s"http://$keycloakServer:$keycloakPort/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.CertifyKeycloak.CLIENT_ID)
  val configUrl: String =
    s"http://$keycloakServer:$keycloakPort/auth/realms/poc-certify/.well-known/openid-configuration"
  lazy val acceptedKid: String = kid.body
    .right
    .get
    .keys
    .find(_.alg == "ES256")
    .get.kid
}

@Singleton
class TestKeycloakDeviceConfig @Inject() (val conf: Config) extends KeycloakDeviceConfig with LazyLogging {

  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  implicit private val formats: Formats = DefaultFormats.lossless ++ TestFormats.all
  private val defaultPort = 8081
  private val isLocalDockerRunning = isPortInUse(defaultPort)

  private val keycloakServer =
    if (isLocalDockerRunning) {
      "localhost"
    } else {
      KeycloakDeviceContainer.container.container.getContainerIpAddress
    }
  private val keycloakPort =
    if (isLocalDockerRunning) {
      defaultPort
    } else {
      KeycloakDeviceContainer.container.container.getFirstMappedPort
    }

  private lazy val kid = {
    basicRequest
      .get(uri"http://$keycloakServer:$keycloakPort/auth/realms/ubirch-default-realm/protocol/openid-connect/certs")
      .response(asJson[KeycloakKidResponse])
      .send()
  }

  val serverUrl: String = s"http://$keycloakServer:$keycloakPort/auth"
  val serverRealm: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.SERVER_REALM)
  val username: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.USERNAME)
  val password: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.PASSWORD)
  val clientId: String = conf.getString(ConfPaths.KeycloakPaths.DeviceKeycloak.CLIENT_ID)
  val configUrl: String =
    s"http://$keycloakServer:$keycloakPort/auth/realms/ubirch-default-realm/.well-known/openid-configuration"
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

object StaticTestPostgresJdbcContext extends LazyLogging {
  private val defaultPort = 5432
  private val isLocalDockerRunning = isPortInUse(defaultPort)
  val portNumber = if (isLocalDockerRunning) defaultPort else PostgresDbContainer.container.container.getFirstMappedPort
  val serverName =
    if (isLocalDockerRunning) "localhost" else PostgresDbContainer.container.container.getContainerIpAddress

  val ctx: PostgresMonixJdbcContext[SnakeCase] = new PostgresMonixJdbcContext(
    SnakeCase,
    ConfigFactory.parseString(s"""
                                 |    dataSourceClassName = org.postgresql.ds.PGSimpleDataSource
                                 |    dataSource.user = postgres
                                 |    dataSource.password = postgres
                                 |    dataSource.databaseName = postgres
                                 |    dataSource.portNumber = $portNumber
                                 |    dataSource.serverName = $serverName
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
        classOf[TestKeycloakCertifyConfig].getConstructor(classOf[Config])
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
