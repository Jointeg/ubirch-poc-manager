package com.ubirch.services.teamdrive

import com.typesafe.config.Config
import com.ubirch.ConfPaths.TeamDrivePaths

import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.Duration

trait TeamDriveClientConfig {
  def url: String
  def username: String
  def password: String
  def readTimeout: Duration
}

@Singleton
class TypesafeTeamDriveClientConfig @Inject() (config: Config) extends TeamDriveClientConfig {
  override def url: String = config.getString(TeamDrivePaths.URL)

  override def username: String = config.getString(TeamDrivePaths.USERNAME)

  override def password: String = config.getString(TeamDrivePaths.PASSWORD)

  override def readTimeout: Duration = Duration.fromNanos(config.getDuration(TeamDrivePaths.READ_TIMEOUT).getNano)
}
