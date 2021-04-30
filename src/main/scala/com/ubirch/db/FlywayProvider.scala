package com.ubirch.db

import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.ConfPaths.PostgresPaths
import org.flywaydb.core.Flyway

import javax.inject.Singleton

trait FlywayProvider {
  def getFlyway: Flyway
}

@Singleton
case class FlywayProviderImpl @Inject()(conf: Config) extends FlywayProvider {

  private val serverName = conf.getString(PostgresPaths.SERVER_NAME)
  private val port = conf.getString(PostgresPaths.PORT)
  private val user = conf.getString(PostgresPaths.USER)
  private val password = conf.getString(PostgresPaths.PASSWORD)
  private val databaseName = conf.getString(PostgresPaths.DATABASE_NAME)

  val flyway = Flyway
    .configure()
    .dataSource(
      s"jdbc:postgresql://$serverName:$port/$databaseName",
      user,
      password
    )
    .schemas("poc_manager")
    .load()

  def getFlyway: Flyway = flyway
}
