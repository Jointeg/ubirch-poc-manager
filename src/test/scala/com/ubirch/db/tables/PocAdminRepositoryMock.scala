package com.ubirch.db.tables

import com.ubirch.models.poc.PocAdmin
import com.ubirch.models.tenant.TenantId
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }
import scala.collection.mutable

@Singleton
class PocAdminRepositoryMock @Inject() (pocAdminStatusRepositoryMock: PocAdminStatusRepositoryMock)
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
}
