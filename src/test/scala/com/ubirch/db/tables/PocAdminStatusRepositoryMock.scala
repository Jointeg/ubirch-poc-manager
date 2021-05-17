package com.ubirch.db.tables

import com.ubirch.models.poc.PocAdminStatus
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocAdminStatusRepositoryMock extends PocAdminStatusRepository {
  private val pocAdminDatastore = mutable.Map[UUID, PocAdminStatus]()

  def createStatus(pocAdminStatus: PocAdminStatus): Task[UUID] = Task {
    pocAdminDatastore += pocAdminStatus.pocAdminId -> pocAdminStatus
    pocAdminStatus.pocAdminId
  }

  def getStatus(pocAdminId: UUID): Task[Option[PocAdminStatus]] = {
    Task(pocAdminDatastore.get(pocAdminId))
  }
}
