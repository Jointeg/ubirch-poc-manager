package com.ubirch.db.tables

import com.ubirch.db.tables.model.PaginatedResult
import com.ubirch.models.poc.{ Aborted, Completed, Poc, PocAdmin, Processing }
import com.ubirch.models.tenant.TenantId
import monix.eval.Task

import java.lang
import java.util.UUID
import javax.inject.{ Inject, Singleton }
import scala.collection.mutable

@Singleton
class PocAdminRepositoryMock @Inject() (
  pocAdminStatusRepositoryMock: PocAdminStatusRepositoryMock,
  pocRepository: PocRepository)
  extends PocAdminRepository {
  private val pocAdminDatastore = mutable.Map[UUID, PocAdmin]()

  def createPocAdmin(pocAdmin: PocAdmin): Task[UUID] = {
    Task {
      pocAdminDatastore += (pocAdmin.id -> pocAdmin)
      pocAdmin.id
    }
  }

  def updatePocAdmin(pocAdmin: PocAdmin): Task[UUID] = {
    Task {
      pocAdminDatastore.update(pocAdmin.id, pocAdmin)
      pocAdmin.id
    }
  }

  def getPocAdmin(pocAdminId: UUID): Task[Option[PocAdmin]] = {
    Task(pocAdminDatastore.get(pocAdminId))
  }

  def getAllPocAdminsByTenantId(tenantId: TenantId): Task[List[PocAdmin]] = {
    Task {
      pocAdminDatastore.collect {
        case (_, pocAdmin: PocAdmin) if pocAdmin.tenantId == tenantId => pocAdmin
      }.toList
    }
  }

  private def getAllUncompletedPocAdmins(): Task[List[PocAdmin]] = {
    Task {
      pocAdminDatastore.collect {
        case (_, pocAdmin: PocAdmin) if pocAdmin.status != Completed && pocAdmin.status != Aborted => pocAdmin
      }.toList
    }
  }

  def assignWebIdentInitiateId(pocAdminId: UUID, webIdentInitiateId: UUID): Task[Unit] = Task {
    pocAdminDatastore.update(
      pocAdminId,
      pocAdminDatastore(pocAdminId).copy(webIdentInitiateId = Some(webIdentInitiateId)))
  }.flatMap(_ => pocAdminStatusRepositoryMock.updateWebIdentInitiated(pocAdminId, webIdentInitiated = true))

  override def updateWebIdentIdAndStatus(
    webIdentId: String,
    pocAdminId: UUID): Task[Unit] = Task {
    pocAdminDatastore.update(pocAdminId, pocAdminDatastore(pocAdminId).copy(webIdentId = Some(webIdentId)))
  }.flatMap(_ => pocAdminStatusRepositoryMock.updateWebIdentSuccess(pocAdminId, webIdentSuccess = true))

  override def getAllByCriteria(criteria: model.Criteria): Task[model.PaginatedResult[(PocAdmin, Poc)]] = {
    val all = pocAdminDatastore.values
      .filter(_.tenantId == criteria.tenantId)
      .map { pa =>
        Task.parZip2(
          Task.pure(pa),
          pocRepository.getPoc(pa.pocId).flatMap {
            case Some(value) => Task.pure(value)
            case None => Task.raiseError(
                new RuntimeException(s"PoC with id ${pa.pocId} does not exists for PocAdmin with id ${pa.id}"))
          }
        )
      }
    Task.sequence(all).map(i => PaginatedResult(i.size, i.toSeq))
  }
  override def getByCertifyUserId(certifyUserId: UUID): Task[Option[PocAdmin]] = Task {
    pocAdminDatastore.find {
      case (_, pocAdmin) if pocAdmin.certifyUserId.contains(certifyUserId) => true
      case _                                                               => false
    }.map(_._2)
  }

  override def getAllPocAdminsToBecomeProcessed(): Task[List[UUID]] = getAllUncompletedPocAdmins().map(_.map(_.id))
  override def unsafeGetUncompletedPocAdminById(id: UUID): Task[PocAdmin] =
    getAllUncompletedPocAdmins().map(_.find(_.id == id).head)

  override def getByPocId(pocId: UUID): Task[List[PocAdmin]] = Task {
    pocAdminDatastore.filter {
      case (_, pocAdmin) if pocAdmin.pocId == pocId => true
      case _                                        => false
    }.map(_._2).toList
  }

  override def incrementCreationAttempts(pocAdminId: UUID): Task[Unit] = getPocAdmin(pocAdminId).flatMap {
    case Some(admin) => updatePocAdmin(admin.copy(creationAttempts = admin.creationAttempts + 1)).void
    case None        => Task.raiseError(new lang.RuntimeException(s"Could not find PoC Admin with ID: $pocAdminId"))
  }

  override def getAllAbortedByPocId(pocId: UUID): Task[List[PocAdmin]] =
    getByPocId(pocId).map(_.filter(_.status == Aborted))

  override def retryAllPocAdmins(pocId: UUID): Task[Unit] = {
    pocAdminDatastore.foreach {
      case (_, pocAdmin: PocAdmin) if pocAdmin.pocId == pocId && pocAdmin.status == Aborted =>
        updatePocAdmin(pocAdmin.copy(status = Processing, creationAttempts = 0)).void
      case _ => Task.unit
    }
    Task()
  }
}
