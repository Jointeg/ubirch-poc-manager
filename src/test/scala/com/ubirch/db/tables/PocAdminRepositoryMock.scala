package com.ubirch.db.tables

import com.ubirch.models.poc.{ Completed, PocAdmin }
import com.ubirch.models.tenant.TenantId
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocAdminRepositoryMock extends PocAdminRepository {
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

  def getAllUncompletedPocs(): Task[List[PocAdmin]] = {
    Task {
      pocAdminDatastore.collect {
        case (_, pocAdmin: PocAdmin) if pocAdmin.status != Completed => pocAdmin
      }.toList
    }
  }
}
