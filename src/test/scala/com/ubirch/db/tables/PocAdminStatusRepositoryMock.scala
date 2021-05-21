package com.ubirch.db.tables

import com.ubirch.models.poc.PocAdminStatus
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocAdminStatusRepositoryMock extends PocAdminStatusRepository {
  private val pocAdminDatastore = mutable.Map[UUID, PocAdminStatus]()

  def createStatus(pocAdminStatus: PocAdminStatus): Task[Unit] = Task {
    pocAdminDatastore += pocAdminStatus.pocAdminId -> pocAdminStatus
  }

  def getStatus(pocAdminId: UUID): Task[Option[PocAdminStatus]] = {
    Task(pocAdminDatastore.get(pocAdminId))
  }

  def updateStatus(pocAdminStatus: PocAdminStatus): Task[Unit] =
    Task(pocAdminDatastore.update(pocAdminStatus.pocAdminId, pocAdminStatus))

  def updateWebIdentSuccess(
    pocAdminId: UUID,
    webIdentSuccess: Boolean): Task[Unit] = Task {
    pocAdminDatastore.update(
      pocAdminId,
      pocAdminDatastore(pocAdminId).copy(webIdentSuccess = Some(webIdentSuccess)))
  }

  def updateWebIdentInitiated(pocAdminId: UUID, webIdentInitiated: Boolean): Task[Unit] = Task {
    pocAdminDatastore.update(
      pocAdminId,
      pocAdminDatastore(pocAdminId).copy(webIdentInitiated = Some(webIdentInitiated)))
  }

}
