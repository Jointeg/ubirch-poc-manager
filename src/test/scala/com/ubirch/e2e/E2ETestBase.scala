package com.ubirch.e2e

import com.typesafe.scalalogging.StrictLogging
import com.ubirch._
import com.ubirch.models.user.UserName
import com.ubirch.services.keycloak._
import org.flywaydb.core.Flyway
import org.scalatest.{ EitherValues, OptionValues }
import org.scalatra.test.scalatest.ScalatraWordSpec

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
    cleanupDB()
    performFlywayMigration()
    try {
      testCode(injector)
    } finally {
      performKeycloakCleanup(injector)
    }
  }

  private def getRandomString[A] = {
    Random.alphanumeric.take(10).mkString("")
  }

  private def cleanupDB() = {
    PostgresDbContainer.flyway.clean()
  }

  private def performFlywayMigration() = {
    PostgresDbContainer.flyway.migrate()
  }

  private def performKeycloakCleanup(injector: E2EInjectorHelperImpl): Unit = {
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

object KeycloakCertifyContainer {
  val container: KeycloakContainer =
    KeycloakContainer.Def(mountExtension = true, realmExportFile = "certify-export.json").start()
}

object KeycloakDeviceContainer {
  val container: KeycloakContainer =
    KeycloakContainer.Def(mountExtension = false, realmExportFile = "device-export.json").start()
}

object PostgresDbContainer {
  val container: PostgresContainer = PostgresContainer.Def().start()

  val flyway: Flyway = Flyway
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
