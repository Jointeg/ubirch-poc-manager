package com.ubirch
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ServicesConfPaths
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

import javax.inject.{ Inject, Singleton }

trait PocConfig {
  val pocTypeDataSchemaMap: Map[String, Seq[String]]
  val pocTypePocNameMap: Map[String, String]
  val pocLogoEndpoint: String
}

@Singleton
class PocConfigImpl @Inject() (config: Config) extends PocConfig with LazyLogging {
  implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats

  override val pocTypeDataSchemaMap: Map[String, Seq[String]] =
    try {
      parse(config.getString(ServicesConfPaths.POC_TYPE_DATA_SCHEMA_MAP)).extract[Map[String, Seq[String]]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.POC_TYPE_DATA_SCHEMA_MAP} value as Map[String, String]")
        throw e
    }
  override val pocTypePocNameMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.POC_TYPE_POC_NAME_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.POC_TYPE_POC_NAME_MAP} value as Map[String, String]")
        throw e
    }
  override val pocLogoEndpoint: String = config.getString(ServicesConfPaths.POC_LOGO_ENDPOINT)
}
