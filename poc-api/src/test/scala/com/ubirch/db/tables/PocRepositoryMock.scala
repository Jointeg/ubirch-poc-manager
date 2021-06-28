package com.ubirch.db.tables

import com.google.inject.Inject
import com.ubirch.db.tables.model.{ Criteria, PaginatedResult }
import com.ubirch.models.poc._
import com.ubirch.models.tenant.TenantId
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocRepositoryMock @Inject() (pocStatusTable: PocStatusRepositoryMock) extends PocRepository {
  private val pocDatastore = mutable.Map[UUID, Poc]()

  override def createPoc(poc: Poc): Task[UUID] =
    Task {
      pocDatastore += ((poc.id, poc))
      poc.id
    }

  override def updatePoc(poc: Poc): Task[UUID] =
    Task {
      pocDatastore.update(poc.id, poc)
      poc.id
    }

  override def getAllPocsByTenantId(tenantId: TenantId): Task[List[Poc]] =
    Task {
      pocDatastore.collect {
        case (_, poc: Poc) if poc.tenantId == tenantId => poc
      }.toList
    }

  private def getAllUncompletedPocs(): Task[List[Poc]] =
    Task {
      pocDatastore.collect {
        case (_, poc: Poc) if poc.status != Completed && poc.status != Aborted => poc
      }.toList
    }

  override def unsafeGetUncompletedPocByIds(id: UUID): Task[Poc] =
    getAllUncompletedPocs().map(_.filter(_.id == id).head)

  override def getPoc(pocId: UUID): Task[Option[Poc]] = {
    Task(pocDatastore.get(pocId))
  }

  override def single(id: UUID): Task[Poc] =
    getPoc(id).flatMap {
      case Some(v) => Task.pure(v)
      case None    => Task.raiseError(PocRepository.PocNotFound(id))
    }

  override def deletePoc(id: UUID): Task[Unit] =
    Task(pocDatastore.remove(id))

  override def createPocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Unit] = {
    Task {
      pocDatastore += ((poc.id, poc))
    }.map(_ => pocStatusTable.createPocStatus(pocStatus))
  }

  override def getAllPocsByCriteria(pocCriteria: Criteria): Task[PaginatedResult[Poc]] =
    Task {
      val pocs = pocDatastore.filter { case (_, poc) => poc.tenantId == pocCriteria.tenantId }.values
      PaginatedResult(pocs.size, pocs.toSeq)
    }

  override def getPoCsSimplifiedDeviceInfoByTenant(tenantId: TenantId): Task[List[SimplifiedDeviceInfo]] =
    getAllPocsByTenantId(tenantId).map(pocs =>
      pocs.map(poc => SimplifiedDeviceInfo(poc.externalId, poc.pocName, poc.deviceId)))

  override def getAllUncompletedPocsIds(): Task[List[UUID]] = getAllUncompletedPocs().map(_.map(_.id))

  override def incrementCreationAttempt(id: UUID): Task[Unit] = getPoc(id).flatMap {
    case Some(poc) => updatePoc(poc.copy(creationAttempts = poc.creationAttempts + 1)).void
    case None      => Task.raiseError(PocRepository.PocNotFound(id))
  }
}
