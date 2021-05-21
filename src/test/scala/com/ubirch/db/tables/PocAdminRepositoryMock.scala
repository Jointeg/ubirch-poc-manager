package com.ubirch.db.tables

import com.ubirch.db.tables.model.PaginatedResult
import com.ubirch.models.poc.{ Poc, PocAdmin }
import com.ubirch.models.tenant.TenantId
import monix.eval.Task

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
  override def updatePocAdmin(pocAdmin: PocAdmin): Task[Unit] = Task(pocAdminDatastore.update(pocAdmin.id, pocAdmin))

  override def assignWebIdentInitiateId(pocAdminId: UUID, webIdentInitiateId: UUID): Task[Unit] = Task {
    pocAdminDatastore.update(
      pocAdminId,
      pocAdminDatastore(pocAdminId).copy(webIdentInitiateId = Some(webIdentInitiateId)))
  }
  override def updateWebIdentIdAndStatus(
    webIdentId: UUID,
    pocAdminId: UUID): Task[Unit] = Task {
    pocAdminDatastore.update(pocAdminId, pocAdminDatastore(pocAdminId).copy(webIdentId = Some(webIdentId.toString)))
  }.flatMap(_ => pocAdminStatusRepositoryMock.updateWebIdentIdentified(pocAdminId, webIdentIdentified = true))

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
}
