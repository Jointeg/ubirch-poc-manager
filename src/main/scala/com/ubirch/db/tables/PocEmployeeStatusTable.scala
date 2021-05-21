package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.pocEmployee.PocEmployeeStatus
import io.getquill.{ Delete, EntityQuery, Insert, Update }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocEmployeeStatusRepository {
  def createStatus(status: PocEmployeeStatus): Task[Unit]

  def getStatus(employeeId: UUID): Task[Option[PocEmployeeStatus]]

  def updateStatus(status: PocEmployeeStatus): Task[Unit]

  def deleteStatus(employeeId: UUID): Task[Unit]
}

class PocEmployeeStatusTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext)
  extends PocEmployeeStatusRepository {
  import QuillMonixJdbcContext.ctx._

  private def createStatusQuery(status: PocEmployeeStatus): Quoted[Insert[PocEmployeeStatus]] =
    quote {
      querySchema[PocEmployeeStatus]("poc_manager.poc_employee_status_table").insert(lift(status))
    }

  private def getStatusQuery(employeeId: UUID): Quoted[EntityQuery[PocEmployeeStatus]] =
    quote {
      querySchema[PocEmployeeStatus]("poc_manager.poc_employee_status_table").filter(
        _.pocEmployeeId == lift(employeeId))
    }

  private def updateStatusQuery(status: PocEmployeeStatus): Quoted[Update[PocEmployeeStatus]] =
    quote {
      querySchema[PocEmployeeStatus]("poc_manager.poc_employee_status_table").filter(
        _.pocEmployeeId == lift(status.pocEmployeeId)).update(lift(status))
    }

  private def deleteEmployeePocStatusQuery(employeeId: UUID): Quoted[Delete[PocEmployeeStatus]] =
    quote {
      querySchema[PocEmployeeStatus]("poc_manager.poc_employee_status_table").filter(
        _.pocEmployeeId == lift(employeeId)).delete
    }

  def createStatus(status: PocEmployeeStatus): Task[Unit] = run(createStatusQuery(status)).void

  def getStatus(employeeId: UUID): Task[Option[PocEmployeeStatus]] = run(getStatusQuery(employeeId)).map(_.headOption)

  def updateStatus(status: PocEmployeeStatus): Task[Unit] = run(updateStatusQuery(status)).void

  def deleteStatus(employeeId: UUID): Task[Unit] = run(deleteEmployeePocStatusQuery(employeeId)).void
}
