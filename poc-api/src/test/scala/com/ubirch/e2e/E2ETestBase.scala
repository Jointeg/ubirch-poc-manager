package com.ubirch.e2e

import com.typesafe.scalalogging.StrictLogging
import com.ubirch._
import com.ubirch.e2e.StartupHelperMethods.isPortInUse
import com.ubirch.models.user.UserName
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak._
import com.ubirch.services.poc.PocTestHelper.await
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import monix.execution.Scheduler
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.scalatest.{ EitherValues, OptionValues }
import org.scalatra.test.scalatest.ScalatraWordSpec

import java.security.Key
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter
import scala.util.Random

trait E2ETestBase
  extends ScalatraWordSpec
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues
  with StrictLogging {

  protected def useMockKeyDiscoveryService: DiscoveryServiceType = MockDiscoveryService

  def withInjector[A](testCode: E2EInjectorHelperImpl => A): A = {
    val tenantAdminEmail = s"$getRandomString@email.com"
    val superAdminEmail = s"$getRandomString@email.com"
    val tenantAdmin: TenantAdmin =
      TenantAdmin(UserName(tenantAdminEmail), tenantAdminEmail, Random.alphanumeric.take(10).mkString(""))
    val superAdmin: SuperAdmin =
      SuperAdmin(
        UserName(Random.alphanumeric.take(10).mkString("")),
        superAdminEmail,
        Random.alphanumeric.take(10).mkString(""))
    val injector = new E2EInjectorHelperImpl(superAdmin, tenantAdmin, useMockKeyDiscoveryService)
    KeyPoolServiceInitialization.initPool(injector)
    prepareDB()
    testCode(injector)
  }

  def getRandomString: String = {
    Random.alphanumeric.take(10).mkString("")
  }

  private def prepareDB() = {
    val truncateStatement =
      "TRUNCATE poc_manager.poc_admin_table, poc_manager.poc_employee_table, poc_manager.poc_table, poc_manager.tenant_table, poc_manager.user_table, poc_manager.poc_admin_status_table, poc_manager.poc_employee_status_table, poc_manager.poc_status_table, poc_manager.key_hash_table CASCADE"
    if (isPortInUse(5432)) {
      await(StaticTestPostgresJdbcContext.ctx.executeAction(truncateStatement), 2.seconds)
    } else {
      InitDatabase.migration
      await(StaticTestPostgresJdbcContext.ctx.executeAction(truncateStatement), 2.seconds)
    }
  }

  def performKeycloakCleanup(injector: E2EInjectorHelperImpl): Unit = {
    val keycloakUsers = injector.get[CertifyKeycloakConnector]
    val keycloakDevice = injector.get[DeviceKeycloakConnector]

    val keycloakCertifyRealm = keycloakUsers.keycloak.realm(CertifyDefaultRealm.name)
    val keycloakCertifyUbirchRealm = keycloakUsers.keycloak.realm(CertifyUbirchRealm.name)
    val keycloakCertifyBmgRealm = keycloakUsers.keycloak.realm(CertifyBmgRealm.name)
    val keycloakDeviceRealm = keycloakDevice.keycloak.realm(DeviceDefaultRealm.name)
    val keycloakRealms =
      Seq(keycloakCertifyRealm, keycloakCertifyUbirchRealm, keycloakDeviceRealm, keycloakCertifyBmgRealm)

    keycloakRealms.foreach { realm =>
      realm.users().list().asScala.foreach(user => realm.users().delete(user.getId))
      realm.groups().groups().asScala.foreach(group => {
        realm.groups().group(group.getId).remove()
      })
      realm.roles().list().asScala.filterNot(role =>
        role.getName == "super-admin" || role.getName == "tenant-admin" || role.getName == "admin").foreach(role =>
        realm.roles().deleteRole(role.getName))
    }
  }
}

object InitDatabase {
  lazy val migration: MigrateResult = PostgresDbContainer.flyway.migrate()
}

object KeyPoolServiceInitialization {
  private var pool: List[(String, Key)] = List.empty
  def initPool(injector: InjectorHelper)(implicit scheduler: Scheduler): List[(String, Key)] = {
    if (pool.isEmpty) {
      val publicKeyPoolService = injector.get[PublicKeyPoolService]
      val initialization = await(publicKeyPoolService.init(DeviceKeycloak, CertifyKeycloak), 2.seconds)
      pool = initialization
      pool
    } else {
      pool
    }
  }
}

object KeycloakCertifyContainer {
  lazy val container: KeycloakContainer =
    KeycloakContainer.Def(mountExtension = true, realmExportFile = "certify-export.json").start()
}

object KeycloakDeviceContainer {
  lazy val container: KeycloakContainer =
    KeycloakContainer.Def(mountExtension = false, realmExportFile = "device-export.json").start()
}

object PostgresDbContainer {
  lazy val container: PostgresContainer = PostgresContainer.Def().start()

  lazy val flyway: Flyway = Flyway
    .configure()
    .dataSource(
      s"jdbc:postgresql://${container.container.getContainerIpAddress}:${container.container.getFirstMappedPort}/postgres",
      "postgres",
      "postgres"
    )
    .schemas("poc_manager")
    .load()
}

sealed trait DiscoveryServiceType {
  def isMock: Boolean = this match {
    case MockDiscoveryService => true
    case RealDiscoverService  => false
  }
}
case object MockDiscoveryService extends DiscoveryServiceType
case object RealDiscoverService extends DiscoveryServiceType

object StartupHelperMethods {
  import java.io.IOException
  import java.net.ServerSocket

  def isPortInUse(port: Int): Boolean = {
    var ss: ServerSocket = null
    try {
      ss = new ServerSocket(port)
      ss.setReuseAddress(true)
      return false
    } catch {
      case _: IOException =>

    } finally {
      if (ss != null)
        try ss.close()
        catch {
          case _: IOException =>
        }
    }
    true
  }
}
