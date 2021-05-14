package com.ubirch

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths

import javax.inject.{ Inject, Singleton }
import org.json4s.jackson.JsonMethods.parse

trait PocConfig {
  val dataSchemaGroupMap: Map[String, String]
}

@Singleton
class PocConfigImpl @Inject() (config: Config) extends PocConfig with LazyLogging {
  implicit val formats = org.json4s.DefaultFormats

  val dataSchemaGroupMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.DATA_SCHEMA_GROUP_MAP)).extract[Map[String, String]]
    } catch {
      case e =>
        logger.error(s"can't parse the ${ServicesConfPaths.DATA_SCHEMA_GROUP_MAP} value as Map[String, String]")
        throw e
    }
}
