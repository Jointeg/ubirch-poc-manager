package com.ubirch

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ServicesConfPaths, TeamDrivePaths }
import com.ubirch.util.CertMaterializer
import org.bouncycastle.cert.X509CertificateHolder
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

import javax.inject.{ Inject, Singleton }
import scala.concurrent.duration.{ Duration, DurationInt, FiniteDuration }

trait PocConfig {

  val dataSchemaGroupMap: Map[String, String]
  val pocTypeDataSchemaMap: Map[String, Seq[String]]
  val trustedPocGroupMap: Map[String, String]
  val pocTypeEndpointMap: Map[String, String]
  val pocTypePocNameMap: Map[String, String]
  val locationNeeded: Seq[String]
  val roleNeeded: Seq[String]

  val teamDriveAdminEmails: Seq[String]
  val teamDriveStage: String
  val pocLogoEndpoint: String
  val certWelcomeMessage: String
  val staticAssetsWelcomeMessage: String
  val pocTypeStaticSpaceNameMap: Map[String, String]
  val issuerCertMap: Map[String, X509CertificateHolder]
  val elementsProcessingTimeout: Duration
  val waitingForNewElementsTimeout: Duration
  val startupTimeout: Duration
  val generalTimeout: Int
  val loopCancellationTimeout: Int
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

  val pocTypeDataSchemaMap: Map[String, Seq[String]] =
    try {
      parse(config.getString(ServicesConfPaths.POC_TYPE_DATA_SCHEMA_MAP)).extract[Map[String, Seq[String]]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.POC_TYPE_DATA_SCHEMA_MAP} value as Map[String, String]")
        throw e
    }

  val pocTypePocNameMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.POC_TYPE_POC_NAME_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.POC_TYPE_POC_NAME_MAP} value as Map[String, String]")
        throw e
    }

  val trustedPocGroupMap: Map[String, String] =
    try {
      parse(config.getString(ServicesConfPaths.TRUSTED_POC_GROUP_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${ServicesConfPaths.TRUSTED_POC_GROUP_MAP} value as Map[String, String]")
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
  val pocLogoEndpoint: String = config.getString(ServicesConfPaths.POC_LOGO_ENDPOINT)
  val certWelcomeMessage: String = config.getString(TeamDrivePaths.CERT_WELCOME_MESSAGE)
  val staticAssetsWelcomeMessage: String = config.getString(TeamDrivePaths.STATIC_ASSETS_WELCOME_MESSAGE)

  val pocTypeStaticSpaceNameMap: Map[String, String] =
    try {
      parse(config.getString(TeamDrivePaths.POC_TYPE_STATIC_SPACE_NAME_MAP)).extract[Map[String, String]]
    } catch {
      case e: Exception =>
        logger.error(s"can't parse the ${TeamDrivePaths.POC_TYPE_STATIC_SPACE_NAME_MAP} value as Map[String, String]")
        throw e
    }

  val issuerCertMap: Map[String, X509CertificateHolder] = {
    lazy val chainPems = config.getString(ServicesConfPaths.POC_ISSUE_CERTS).trim
    if (config.getIsNull(ServicesConfPaths.POC_ISSUE_CERTS) || chainPems.isEmpty) {
      logger.debug("No chain pems configured")
      Map.empty[String, X509CertificateHolder]
    } else {
      //We want to fail if it is not properly created
      val holders = chainPems.split(";").toList.map(CertMaterializer.parse).map(_.get).distinct
      holders.map { holder => holder.getSubject.toString -> holder }.toMap
    }
  }

  override val elementsProcessingTimeout: FiniteDuration =
    config.getInt(ConfPaths.HealthChecks.Timeouts.ELEMENTS_PROCESSING).minutes
  override val waitingForNewElementsTimeout: FiniteDuration =
    config.getInt(ConfPaths.HealthChecks.Timeouts.WAITING_FOR_NEW_ELEMENTS).minutes
  override val startupTimeout: FiniteDuration = config.getInt(ConfPaths.HealthChecks.Timeouts.STARTUP).minutes
  override val generalTimeout: Int = config.getInt(ConfPaths.Lifecycle.GENERAL_TIMEOUT)
  override val loopCancellationTimeout: Int = generalTimeout - 2
}
