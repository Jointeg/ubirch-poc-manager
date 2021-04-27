package com.ubirch.e2e

import com.dimafeng.testcontainers.lifecycle.and
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.models.user.UserName
import com.ubirch._
import monix.eval.Task
import org.flywaydb.core.Flyway
import org.scalatest.{EitherValues, OptionValues}
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.concurrent.duration.DurationInt
import scala.util.Random

trait E2ETestBase
  extends ScalatraWordSpec
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues
  with TestContainersForAll
  with StrictLogging {

  override type Containers = PostgresContainer and KeycloakContainer and KeycloakContainer

  override def startContainers(): Containers = {
    logger.info("Starting up test containers")

    val (postgresContainer, keycloakUsersContainer, keycloakDevicesContainer) = await(
      Task.parMap3(
        Task(PostgresContainer.Def().start()),
        Task(KeycloakContainer.Def(mountExtension = true, realmExportFile = "users-export.json").start()),
        Task(KeycloakContainer.Def(mountExtension = false, realmExportFile = "device-export.json").start())
      )((postgresContainer, keycloakUsersContainer, keycloakDevicesContainer) =>
        (postgresContainer, keycloakUsersContainer, keycloakDevicesContainer)),
      60.seconds
    )

    postgresContainer.and(keycloakUsersContainer).and(keycloakDevicesContainer)
  }

  def withInjector[A](testCode: E2EInjectorHelperImpl => A): A = {
    withContainers {
      case postgresContainer and keycloakUsersContainer and keycloakDevicesContainer =>
        Flyway
          .configure()
          .dataSource(
            s"jdbc:postgresql://${postgresContainer.container.getContainerIpAddress}:${postgresContainer.container.getFirstMappedPort}/postgres",
            "postgres",
            "postgres"
          )
          .schemas("poc_manager")
          .load()
          .migrate()
        val tenantAdmin: TenantAdmin =
          TenantAdmin(UserName(Random.alphanumeric.take(10).mkString("")), Random.alphanumeric.take(10).mkString(""))
        val superAdmin: SuperAdmin =
          SuperAdmin(UserName(Random.alphanumeric.take(10).mkString("")), Random.alphanumeric.take(10).mkString(""))
        testCode(
          new E2EInjectorHelperImpl(
            postgresContainer,
            keycloakUsersContainer,
            keycloakDevicesContainer,
            superAdmin,
            tenantAdmin))
    }
  }

}
