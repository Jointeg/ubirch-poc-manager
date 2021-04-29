package com.ubirch.e2e

import com.typesafe.scalalogging.StrictLogging
import com.ubirch._
import com.ubirch.models.user.UserName
import com.ubirch.services.keycloak.{
  DeviceKeycloakConnector,
  KeycloakDeviceConfig,
  KeycloakUsersConfig,
  UsersKeycloakConnector
}
import org.flywaydb.core.Flyway
import org.scalatest.{EitherValues, OptionValues}
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
    val tenantAdmin: TenantAdmin =
      TenantAdmin(UserName(Random.alphanumeric.take(10).mkString("")), Random.alphanumeric.take(10).mkString(""))
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

  private def cleanupDB() = {
    PostgresDbContainer.flyway.clean()
  }

  private def performFlywayMigration() = {
    PostgresDbContainer.flyway.migrate()
  }

  private def performKeycloakCleanup(injector: E2EInjectorHelperImpl) = {
    val keycloakUsers = injector.get[UsersKeycloakConnector]
    val keycloakUsersConfig = injector.get[KeycloakUsersConfig]
    val keycloakDevice = injector.get[DeviceKeycloakConnector]
    val keycloakDeviceConfig = injector.get[KeycloakDeviceConfig]

    val keycloakUsersRealm = keycloakUsers.keycloak.realm(keycloakUsersConfig.realm)
    val keycloakDeviceRealm = keycloakDevice.keycloak.realm(keycloakDeviceConfig.realm)

    keycloakUsersRealm.users().list().asScala.foreach(user => keycloakUsersRealm.users().delete(user.getId))
    keycloakDeviceRealm.users().list().asScala.foreach(user => keycloakDeviceRealm.users().delete(user.getId))
  }
}

object KeycloakUsersContainer {
  val container: KeycloakContainer =
    KeycloakContainer.Def(mountExtension = true, realmExportFile = "users-export.json").start()
}

object KeycloakDeviceContainer {
  val container: KeycloakContainer =
    KeycloakContainer.Def(mountExtension = false, realmExportFile = "device-export.json").start()
}

object PostgresDbContainer {
  val container: PostgresContainer = PostgresContainer.Def().start()

  val flyway = Flyway
    .configure()
    .dataSource(
      s"jdbc:postgresql://${container.container.getContainerIpAddress}:${container.container.getFirstMappedPort}/postgres",
      "postgres",
      "postgres"
    )
    .schemas("poc_manager")
    .load()
}
