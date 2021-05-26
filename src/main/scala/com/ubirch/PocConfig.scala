package com.ubirch

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ServicesConfPaths, TeamDrivePaths }
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

import javax.inject.{ Inject, Singleton }

trait PocConfig {

  val pocTypeDataSchemaMap: Map[String, String]
  val pocTypeTrustedPocMap: Map[String, String]
  val pocTypeEndpointMap: Map[String, String]
  val locationNeeded: Seq[String]
  val roleNeeded: Seq[String]
  val teamDriveAdminEmails: Seq[String]
  val teamDriveStage: String
}

@Singleton
class PocConfigImpl @Inject() (config: Config) extends PocConfig with LazyLogging {
  implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats

  val pocTypeDataSchemaMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.POC_TYPE_DATA_SCHEMA_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.POC_TYPE_DATA_SCHEMA_MAP} value as Map[String, String]")
        throw e
    }

  val pocTypeTrustedPocMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.POC_TYPE_TRUSTED_POC_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.POC_TYPE_TRUSTED_POC_MAP} value as Map[String, String]")
        throw e
    }

  val pocTypeEndpointMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.POC_TYPE_ENDPOINT_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.POC_TYPE_ENDPOINT_MAP} value as Map[String, String]")
        throw e
    }

  val locationNeeded: Seq[String] =
    config.getString(ServicesConfPaths.POC_TYPES_LOCATION_NEEDED).split(",").map(_.trim)

  val roleNeeded: Seq[String] =
    config.getString(ServicesConfPaths.POC_TYPES_ROLE_NEEDED).split(",").map(_.trim)

  val teamDriveAdminEmails: Seq[String] =
    config.getString(TeamDrivePaths.UBIRCH_ADMINS).split(",").map(_.trim)

  val teamDriveStage: String = config.getString(TeamDrivePaths.STAGE)
}
