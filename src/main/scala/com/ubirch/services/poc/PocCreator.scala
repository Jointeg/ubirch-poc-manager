package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.tables.{ PocRepository, PocStatusRepository, TenantRepository }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.Tenant
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

trait PocCreator {

  def createPocs(): Task[PocCreationResult]
}

class PocCreatorImpl @Inject() (
  //  certHandler: CertHandler,
  deviceCreator: DeviceCreator,
  informationProvider: InformationProvider,
  keycloakHelper: KeycloakHelper,
  pocTable: PocRepository,
  pocStatusTable: PocStatusRepository,
  tenantTable: TenantRepository)(implicit formats: Formats)
  extends PocCreator
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization
  import deviceCreator._
  import informationProvider._
  import keycloakHelper._

  override def createPocs(): Task[PocCreationResult] = {
    pocTable.getAllUncompletedPocs().flatMap {
      case pocs if pocs.isEmpty =>
        logger.debug("no pocs waiting for completion")
        Task(PocCreationSuccess())
      case pocs =>
        logger.info(s"starting to create ${pocs.size} pocs")
        Task
          .gather(pocs.map(createPoc))
          .map(PocCreationMaybeSuccess)
    }
  }

  private def createPoc(poc: Poc): Task[Either[String, PocStatus]] = {
    retrieveStatusAndTenant(poc).map {
      case (Some(status: PocStatus), Some(tenant: Tenant)) =>
        process(poc, status, tenant)

      case (_, _) =>
        val errorMsg = s"cannot create poc with id ${poc.id} as tenant or status couldn't be found"
        logger.error(errorMsg)
        Task(Left(errorMsg))
    }.flatten
  }

  private def retrieveStatusAndTenant(poc: Poc): Task[(Option[PocStatus], Option[Tenant])] = {
    for {
      status <- pocStatusTable.getPocStatus(poc.id)
      tenant <- tenantTable.getTenant(poc.tenantId)
    } yield (status, tenant)
  }

  //Todo: create and provide client certs
  //Todo: download and store logo
  private def process(poc: Poc, status: PocStatus, tenant: Tenant): Task[Either[String, PocStatus]] = {
    logger.info(s"starting to create poc with id ${poc.id}")
    val creationResult = for {
      status1 <- doUserRealmRelatedTasks(poc, status, tenant)
      status2 <- doDeviceRealmRelatedTasks(poc, status1, tenant)
      status3 <- createDevice(poc, status2, tenant)
      status4 <- infoToGoClient(poc, status3)
      status5 <- infoToCertifyAPI(poc, status4)
      _ <- pocStatusTable.updatePocStatus(status)
    } yield status5

    creationResult
      .map { status =>
        pocStatusTable
          .updatePocStatus(status)
          .map(_ => Right(status))
      }.onErrorHandle(handlePocCreationError).flatten
  }

  private def doDeviceRealmRelatedTasks(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocStatus] = {
    for {
      status1 <- createDeviceRole(poc, status)
      pocAndStatus <- createDeviceGroup(poc, status1, tenant)
      status3 <- assignDeviceRealmRoleToGroup(pocAndStatus, tenant)
    } yield status3
  }

  private def doUserRealmRelatedTasks(poc: Poc, status: PocStatus, tenant: Tenant): Task[PocStatus] = {
    for {
      status4 <- createUserRole(poc, status)
      pocAndStatus <- createUserGroup(poc, status4, tenant)
      status6 <- assignUserRoleToGroup(pocAndStatus, tenant)
    } yield status6
  }

  private def handlePocCreationError(ex: Throwable): Task[Left[String, Nothing]] = {
    ex match {
      case pce: PocCreationError =>
        pocStatusTable
          .updatePocStatus(pce.pocStatus)
          .map { _ =>
            val msg = s"updated poc status after poc creation failed; ${pce.pocStatus}"
            logger.error(msg)
            Left(msg)
          }.onErrorHandle { ex =>
            val errorMsg = s"couldn't persist poc status after failed poc creation ${pce.pocStatus}"
            logger.error(errorMsg, ex)
            Left(errorMsg)
          }

      case ex: Throwable =>
        val errorMsg = "unexpected error during poc creation; "
        logger.error(errorMsg, ex)
        Task(Left(errorMsg + ex.getMessage))
    }
  }

}

case class StatusAndPW(pocStatus: PocStatus, devicePassword: String)

case class PocCreationError(pocStatus: PocStatus) extends Exception

trait PocCreationResult
case class PocCreationFailure() extends PocCreationResult
case class PocCreationSuccess() extends PocCreationResult
case class PocCreationMaybeSuccess(list: Seq[Either[String, PocStatus]]) extends PocCreationResult
