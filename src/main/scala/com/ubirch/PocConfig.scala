package com.ubirch

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import org.json4s.DefaultFormats

import javax.inject.{ Inject, Singleton }
import org.json4s.jackson.JsonMethods.parse

trait PocConfig {
  val dataSchemaGroupMap: Map[String, String]
  val dataSchemaGroupIdMap: Map[String, String]
  val endpointMap: Map[String, String]
}

@Singleton
class PocConfigImpl @Inject() (config: Config) extends PocConfig with LazyLogging {
  implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats

  val dataSchemaGroupMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.DATA_SCHEMA_GROUP_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.DATA_SCHEMA_GROUP_MAP} value as Map[String, String]")
        throw e
    }

  val dataSchemaGroupIdMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.DATA_SCHEMA_GROUP_ID_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.DATA_SCHEMA_GROUP_ID_MAP} value as Map[String, String]")
        throw e
    }

  val endpointMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.ENDPOINT_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.ENDPOINT_MAP} value as Map[String, String]")
        throw e
    }
}
