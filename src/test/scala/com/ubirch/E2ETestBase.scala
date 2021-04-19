package com.ubirch
import com.dimafeng.testcontainers.lifecycle.and
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import com.ubirch.models.keycloak.user.UserName
import org.flywaydb.core.Flyway
import org.scalatest.{EitherValues, OptionValues}
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.util.Random

trait E2ETestBase
  extends ScalatraWordSpec
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues
  with TestContainersForAll {

  override type Containers = PostgresContainer and KeycloakContainer

  override def startContainers(): Containers = {
    val postgresContainer = PostgresContainer.Def().start()
    val keycloakContainer = KeycloakContainer.Def().start()
    postgresContainer.and(keycloakContainer)
  }

  def withInjector[A](testCode: E2EInjectorHelperImpl => A): A = {
    withContainers {
      case postgresContainer and keycloakContainer =>
        val migrationResult = Flyway
          .configure()
          .dataSource(
            s"jdbc:postgresql://${postgresContainer.container.getContainerIpAddress}:${postgresContainer.container.getFirstMappedPort}/postgres",
            "postgres",
            "postgres"
          )
          .schemas("poc_manager")
          .load()
          .migrate()
        if (migrationResult.warnings.isEmpty) {
          val clientAdmin: ClientAdmin =
            ClientAdmin(UserName(Random.alphanumeric.take(10).mkString("")), Random.alphanumeric.take(10).mkString(""))
          testCode(new E2EInjectorHelperImpl(postgresContainer, keycloakContainer, clientAdmin))
        } else {
          fail("Could not start test code because error has happened during ")
        }
    }
  }

}
