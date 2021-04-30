package com.ubirch.e2e

import com.dimafeng.testcontainers.{ ContainerDef, JdbcDatabaseContainer, SingleContainer }
import org.testcontainers.containers.{ PostgreSQLContainer => JavaPostgreSQLContainer }
import org.testcontainers.utility.MountableFile

import scala.concurrent.duration.DurationInt

class PostgresContainer(
  databaseName: Option[String] = None,
  pgUsername: Option[String] = None,
  pgPassword: Option[String] = None
) extends SingleContainer[JavaPostgreSQLContainer[_]]
  with JdbcDatabaseContainer {

  override val container: JavaPostgreSQLContainer[_] = {
    val c: JavaPostgreSQLContainer[_] = new JavaPostgreSQLContainer("postgres:13.2")

    databaseName.foreach(c.withDatabaseName)
    pgUsername.foreach(c.withUsername)
    pgPassword.foreach(c.withPassword)
    c.withStartupTimeoutSeconds(60.seconds.toSeconds.toInt)
    c.withConnectTimeoutSeconds(60.seconds.toSeconds.toInt)
    c.withCopyFileToContainer(
      MountableFile.forHostPath("./sql/initial_tables.sql"),
      "/docker-entrypoint-initdb.d/initial_tables.sql"
    )
    c
  }

  def testQueryString: String = container.getTestQueryString
}

object PostgresContainer {

  val defaultDatabaseName = "postgres"
  val defaultUsername = "postgres"
  val defaultPassword = "postgres"

  def apply(
    databaseName: String = null,
    username: String = null,
    password: String = null
  ): PostgresContainer =
    new PostgresContainer(
      Option(databaseName),
      Option(username),
      Option(password)
    )

  case class Def(
    databaseName: String = defaultDatabaseName,
    username: String = defaultUsername,
    password: String = defaultPassword
  ) extends ContainerDef {

    override type Container = PostgresContainer

    override def createContainer(): PostgresContainer = {
      new PostgresContainer(
        databaseName = Some(databaseName),
        pgUsername = Some(username),
        pgPassword = Some(password)
      )
    }
  }
}
