package com.ubirch.db.tables
import com.ubirch.models.pocEmployee.PocEmployeeStatus
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocEmployeeStatusRepositoryMock extends PocEmployeeStatusRepository {
  private val employeeStatusStore = mutable.Map[UUID, PocEmployeeStatus]()

  override def createStatus(status: PocEmployeeStatus): Task[Unit] = Task {
    employeeStatusStore += status.pocEmployeeId -> status
  }

  def getStatus(pocEmployeeId: UUID): Task[Option[PocEmployeeStatus]] = {
    Task(employeeStatusStore.get(pocEmployeeId))
  }

  def updateStatus(pocEmployeeStatus: PocEmployeeStatus): Task[Unit] =
    Task(employeeStatusStore.update(pocEmployeeStatus.pocEmployeeId, pocEmployeeStatus))

  override def deleteStatus(employeeId: UUID): Task[Unit] =
    Task(employeeStatusStore.remove(employeeId))
}
