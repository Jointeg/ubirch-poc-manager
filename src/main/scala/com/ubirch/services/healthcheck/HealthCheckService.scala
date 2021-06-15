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

import javax.inject.Inject
import scala.concurrent.duration.DurationInt

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
    case common.Running =>
      HealthyService
    case common.Starting =>
      logger.error(s"$loopName is starting")
      UnhealthyService
    case common.Cancelled =>
      logger.error(s"$loopName is cancelled")
      UnhealthyService
    case common.ErrorTerminated =>
      logger.error(s"$loopName is terminated because unexpected error has happened")
      UnhealthyService
    case common.Completed =>
      logger.error(s"$loopName has been completed")
      UnhealthyService
  })

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
