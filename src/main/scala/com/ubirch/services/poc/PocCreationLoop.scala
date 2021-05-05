package com.ubirch.services.poc

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.tables.{ PocStatusTable, PocTable, TenantTable }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.tenant.{ Tenant, TenantId }
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.Future

trait PocCreationLoop {
  def createPocs()
}

case class PocCreationError(pocStatus: PocStatus) extends Exception

class PocCreationLoopImpl @Inject() (
  //  certHandler: CertHandler,
  deviceCreator: DeviceCreator,
  informationProvider: InformationProvider,
  keycloakHelper: KeycloakHelper,
  pocTable: PocTable,
  pocStatusTable: PocStatusTable,
  tenantTable: TenantTable)(implicit formats: Formats)
  extends PocCreationLoop
  with LazyLogging {

  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  implicit private val backend: SttpBackend[Future, Nothing, WebSocketHandler] = AsyncHttpClientFutureBackend()
  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  override def createPocs(): Unit = {
    pocTable.getAllUncompletedPocs().foreach {
      case pocs if pocs.isEmpty =>
        logger.debug("no pocs waiting for completion")
      case pocs =>
        pocs.foreach { poc =>
          retrieveStatusAndTenant(poc).map {
            case (Some(status), Some(tenant)) => process(poc, status, tenant)
            case (status, tenant) =>
              logger.error(
                s"poc cannot become created as pocStatus $status or tenant $tenant couldn't be found for poc with id ${poc.id}")
          }
        }
    }
  }

  private def retrieveStatusAndTenant(poc: Poc): Task[(Option[PocStatus], Option[Tenant])] = {
    for {
      status <- pocStatusTable.getPocStatus(poc.id)
      tenant <- tenantTable.getTenant(TenantId(poc.tenantId))
    } yield (status, tenant)
  }

  private def process(poc: Poc, status: PocStatus, tenant: Tenant): Unit = {
    val creationResult = for {
      //handle device realm
      status1 <- keycloakHelper.createDeviceRealmRole(poc, status)
      status2 <- keycloakHelper.createDeviceRealmGroup(poc, status1, tenant)
      status3 <- keycloakHelper.assignDeviceRealmRoleToGroup(poc, status2, tenant)
      //handle user realm
      status4 <- keycloakHelper.createUserRealmRole(poc, status3)
      status5 <- keycloakHelper.createUserRealmGroup(poc, status4, tenant)
      status6 <- keycloakHelper.assignUserRealmRoleToGroup(poc, status5, tenant)
      //    status7 <- certHandler.createCert(poc, status6)
      //    status8 <- certHandler.provideCert(poc, status7)
      //    status <- logoHandler.retrieveLogo(poc, status8)
      //    status9 <- logoHandler.storeLogo(poc, status9)
      statusAndApi9 <- deviceCreator.createDevice(poc, status6, tenant)
      status10 <- informationProvider.toGoClient(poc, statusAndApi9)
      status11 <- informationProvider.toCertifyAPI(poc, status10)
    } yield status11

    creationResult
      .map(pocStatusTable.updatePocStatus)
      .onErrorHandle(handlePocCreationError)
  }

  private def handlePocCreationError(ex: Throwable): Unit = {
    ex match {
      case pce: PocCreationError =>
        pocStatusTable
          .updatePocStatus(pce.pocStatus)
          .onErrorHandle(logger.error(s"couldn't update poc status in table ${pce.pocStatus}", _))

      case ex: Throwable =>
        logger.error("unexpected error during poc creation", ex)
    }
  }

}
