package com.ubirch

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ServicesConfPaths, TeamDrivePaths }

import javax.inject.{ Inject, Singleton }
import org.json4s.jackson.JsonMethods.parse

trait PocConfig {
  val dataSchemaGroupMap: Map[String, String]
  val teamDriveAdminEmails: Seq[String]
  val teamDriveStage: String
}

@Singleton
class PocConfigImpl @Inject() (config: Config) extends PocConfig with LazyLogging {
  implicit val formats = org.json4s.DefaultFormats

  val dataSchemaGroupMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.DATA_SCHEMA_GROUP_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.DATA_SCHEMA_GROUP_MAP} value as Map[String, String]")
        throw e
    }

  val teamDriveAdminEmails: Seq[String] =
    config.getString(ServicesConfPaths.TEAM_DRIVE_ADMIN_EMAILS).split(",").map(_.trim)

  val teamDriveStage: String = config.getString(TeamDrivePaths.STAGE)
}
