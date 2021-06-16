package com.ubirch.services.healthcheck
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.tables.HealthCheckRepository
import com.ubirch.models.common
import com.ubirch.models.common._
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak._
import com.ubirch.services.poc.{ PocAdminCreationLoop, PocCreationLoop, PocEmployeeCreationLoop }
import com.ubirch.services.teamdrive.model.TeamDriveClient
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakConnector, KeycloakInstance }
import monix.eval.Task
import monix.execution.atomic.AtomicAny
import org.joda.time.DateTime

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.duration.{ Duration, DurationInt }

trait HealthCheckService {
  def performAllHealthChecks(): Task[ReadinessStatus]
}

class DefaultHealthCheckService @Inject() (
  healthCheckRepository: HealthCheckRepository,
  publicKeyPoolService: PublicKeyPoolService,
  keycloakCertifyConfig: KeycloakCertifyConfig,
  keycloakDeviceConfig: KeycloakDeviceConfig,
  keycloakConnector: KeycloakConnector,
  teamDriveClient: TeamDriveClient
) extends HealthCheckService
  with LazyLogging {
  override def performAllHealthChecks(): Task[ReadinessStatus] = {

    val postgresHealthCheck: Task[Service] = performPostgresHealthCheck()
    val kidsHealthCheck = Task.pure(checkIfKidsAreAvailable())
    val certifyDefaultRealmHealthCheck =
      keycloakHealthCheck(keycloakConnector, CertifyKeycloak, CertifyDefaultRealm)
    val certifyBmgRealmHealthCheck =
      keycloakHealthCheck(keycloakConnector, CertifyKeycloak, CertifyBmgRealm)
    val certifyUbirchRealmHealthCheck =
      keycloakHealthCheck(keycloakConnector, CertifyKeycloak, CertifyUbirchRealm)
    val deviceDefaultRealmHealthCheck =
      keycloakHealthCheck(keycloakConnector, DeviceKeycloak, DeviceDefaultRealm)
    val teamDriveHealthCheck = performTeamDriveHealthCheck()
    val pocCreationLoopHealthCheck = performLoopCreationHealthCheck(PocCreationLoop.loopState, "PoC creation loop")
    val pocAdminCreationLoopHealthCheck =
      performLoopCreationHealthCheck(PocAdminCreationLoop.loopState, "PoC admin creation loop")
    val pocEmployeeCreationLoopHealthCheck =
      performLoopCreationHealthCheck(PocEmployeeCreationLoop.loopState, "PoC employee creation loop")

    Task.gather(List(
      postgresHealthCheck,
      kidsHealthCheck,
      certifyDefaultRealmHealthCheck,
      certifyBmgRealmHealthCheck,
      certifyUbirchRealmHealthCheck,
      deviceDefaultRealmHealthCheck,
      teamDriveHealthCheck,
      pocCreationLoopHealthCheck,
      pocAdminCreationLoopHealthCheck,
      pocEmployeeCreationLoopHealthCheck
    )).map(Services).map(_.toReadinessStatus)
  }

  private def performTeamDriveHealthCheck() = {
    teamDriveClient.getLoginInformation().map(_ => HealthyService).onErrorHandle {
      case ex: Exception =>
        logger.error(s"TeamDrive health check returned error: ${ex.getMessage}")
        UnhealthyService
    }
  }

  private def keycloakHealthCheck(
    keycloakConnector: KeycloakConnector,
    keycloakInstance: KeycloakInstance,
    keycloakRealm: KeycloakRealm) = {
    Task(keycloakConnector.getKeycloak(keycloakInstance).realm(keycloakRealm.name).toRepresentation).map(_ =>
      HealthyService).onErrorHandle {
      case ex: Exception =>
        logger.error(s"Keycloak Healthcheck for realm $keycloakRealm returned error: ${ex.getMessage}")
        UnhealthyService
    }
  }

  private def performLoopCreationHealthCheck(state: AtomicAny[LoopState], loopName: String) = Task(state.get() match {
    case common.ProcessingElements(dateTime, elementName, elementId) =>
      val lastTimeProcessed = howLongFromNow(dateTime)
      if (lastTimeProcessed < 5.minutes) {
        logger.debug(s"$loopName is processing $elementName elements. Last one was processed $lastTimeProcessed ago")
        HealthyService
      } else {
        logger.error(
          s"$loopName is processing an $elementName elements with ids: [$elementId] from ${dateTime.toDateTimeISO.toString()}. There is a high chance, that this loop is not responsive anymore")
        UnhealthyService
      }
    case common.WaitingForNewElements(dateTime, elementName) =>
      val lastWaitingTick = howLongFromNow(dateTime)
      if (lastWaitingTick < 5.minutes) {
        logger.debug(
          s"$loopName is waiting for new elements to be processed. Last Waiting tick was $lastWaitingTick ago")
        HealthyService
      } else {
        logger.error(s"$loopName is waiting for $elementName elements from ${dateTime.toDateTimeISO.toString()}. There is a high chance, that this loop is not responsive anymore")
        UnhealthyService
      }
    case common.Starting(dateTime) =>
      val startingFrom = howLongFromNow(dateTime)
      if (startingFrom < 1.minute) {
        logger.debug(s"$loopName is starting from $startingFrom")
        HealthyService
      } else {
        logger.error(s"$loopName is start up time is unexpectedly long. It started $startingFrom ago")
        UnhealthyService
      }
    case common.Cancelled =>
      logger.error(s"$loopName is cancelled")
      UnhealthyService
    case common.ErrorTerminated(dateTime) =>
      logger.error(s"$loopName is terminated because unexpected error has happened at ${dateTime.toDateTimeISO}")
      UnhealthyService
    case common.Completed =>
      logger.error(s"$loopName has been completed")
      UnhealthyService
  })

  private def howLongFromNow(from: DateTime) = {
    Duration(DateTime.now().getMillis - from.getMillis, TimeUnit.MILLISECONDS)
  }

  private def performPostgresHealthCheck() = {
    healthCheckRepository.healthCheck().timeout(1.second).map(_ => HealthyService).onErrorHandle {
      case exception: Exception =>
        logger.error(s"Postgres Health Check returned error: ${exception.getMessage}", exception)
        UnhealthyService
    }
  }

  private def checkIfKidsAreAvailable() = {
    val certifyKid = publicKeyPoolService.getKey(keycloakCertifyConfig.acceptedKid)
    val deviceKid = publicKeyPoolService.getKey(keycloakDeviceConfig.acceptedKid)

    (certifyKid, deviceKid) match {
      case (Some(_), Some(_)) => HealthyService
      case (None, None) =>
        logger.error("Could not find keycloak keys (by kids defined in config file) for certify and device keycloaks")
        UnhealthyService
      case (None, _) =>
        logger.error("Could not find key (by kid defined in the config file) for certify keycloak")
        UnhealthyService
      case (_, None) =>
        logger.error("Could not find key (by kid defined in the config file) for device keycloak")
        UnhealthyService
    }
  }
}

sealed trait ReadinessStatus extends Product with Serializable
case object NotOperational extends ReadinessStatus
case object Operational extends ReadinessStatus

sealed trait Service
case object HealthyService extends Service
case object UnhealthyService extends Service

case class Services(services: List[Service]) {
  def toReadinessStatus: ReadinessStatus = services.find {
    case HealthyService   => false
    case UnhealthyService => true
  }.map(_ => NotOperational).getOrElse(Operational)
}
