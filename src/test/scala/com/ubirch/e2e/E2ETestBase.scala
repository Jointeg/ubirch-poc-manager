package com.ubirch.e2e

import com.typesafe.scalalogging.StrictLogging
import com.ubirch._
import com.ubirch.models.user.UserName
import com.ubirch.services.keycloak.{
  CertifyKeycloakConnector,
  DeviceKeycloakConnector,
  KeycloakCertifyConfig,
  KeycloakDeviceConfig
}
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

  def withInjector[A](testCode: E2EInjectorHelperImpl => A): A = {
    val tenantAdminEmail = s"$getRandomString@email.com"
    val tenantAdmin: TenantAdmin =
      TenantAdmin(UserName(tenantAdminEmail), tenantAdminEmail, Random.alphanumeric.take(10).mkString(""))
    val superAdmin: SuperAdmin =
      SuperAdmin(UserName(Random.alphanumeric.take(10).mkString("")), Random.alphanumeric.take(10).mkString(""))
    val injector = new E2EInjectorHelperImpl(superAdmin, tenantAdmin)
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
    val keycloakCertifyConfig = injector.get[KeycloakCertifyConfig]
    val keycloakDevice = injector.get[DeviceKeycloakConnector]
    val keycloakDeviceConfig = injector.get[KeycloakDeviceConfig]

    val keycloakCertifyRealm = keycloakUsers.keycloak.realm(keycloakCertifyConfig.realm)
    val keycloakDeviceRealm = keycloakDevice.keycloak.realm(keycloakDeviceConfig.realm)

    keycloakCertifyRealm.users().list().asScala.foreach(user => keycloakCertifyRealm.users().delete(user.getId))
    keycloakDeviceRealm.users().list().asScala.foreach(user => keycloakDeviceRealm.users().delete(user.getId))
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
