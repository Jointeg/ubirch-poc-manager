package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.poc.{ Completed, Status }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.TenantId
import io.getquill.{ Delete, EntityQuery, Insert, Update }
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocEmployeeRepository {
  def createPocEmployee(employee: PocEmployee): Task[UUID]

  def updatePocEmployee(pocEmployee: PocEmployee): Task[UUID]

  def getPocEmployee(employeeId: UUID): Task[Option[PocEmployee]]

  def getPocEmployeesByTenantId(tenantId: TenantId): Task[List[PocEmployee]]

  def getUncompletedPocEmployees(): Task[List[PocEmployee]]

  def deletePocEmployee(employeeId: UUID): Task[Unit]

  def getByCertifyUserId(certifyUserId: UUID): Task[Option[PocEmployee]]
}

class PocEmployeeTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends PocEmployeeRepository {
  import QuillMonixJdbcContext.ctx._

  private def createPocEmployeeQuery(employee: PocEmployee): Quoted[Insert[PocEmployee]] =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").insert(lift(employee))
    }

  private def getPocEmployeeQuery(employeeId: UUID): Quoted[EntityQuery[PocEmployee]] =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").filter(_.id == lift(employeeId))
    }

  private def getAllPocEmployeesByTenantIdQuery(tenantId: TenantId): Quoted[EntityQuery[PocEmployee]] =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").filter(_.tenantId == lift(tenantId))
    }

  private def getAllPocsWithoutStatusQuery(status: Status): Quoted[EntityQuery[PocEmployee]] =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").filter(_.status != lift(status))
    }

  private def updatePocEmployeeQuery(pocEmployee: PocEmployee): Quoted[Update[PocEmployee]] =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").filter(_.id == lift(pocEmployee.id)).update(lift(
        pocEmployee))
    }

  private def deleteEmployeeQuery(employeeId: UUID): Quoted[Delete[PocEmployee]] =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").filter(
        _.id == lift(employeeId)).delete
    }

  private def getByCertifyUserIdQuery(certifyUserId: UUID) =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").filter(_.certifyUserId == lift(Option(certifyUserId)))
    }

  def createPocEmployee(employee: PocEmployee): Task[UUID] =
    run(createPocEmployeeQuery(employee)).map(_ => employee.id)

  def getPocEmployee(employeeId: UUID): Task[Option[PocEmployee]] =
    run(getPocEmployeeQuery(employeeId)).map(_.headOption)

  def getPocEmployeesByTenantId(tenantId: TenantId): Task[List[PocEmployee]] = {
    run(getAllPocEmployeesByTenantIdQuery(tenantId))
  }

  def getUncompletedPocEmployees(): Task[List[PocEmployee]] = run(getAllPocsWithoutStatusQuery(Completed))

  def updatePocEmployee(pocEmployee: PocEmployee): Task[UUID] =
    run(updatePocEmployeeQuery(pocEmployee)).map(_ => pocEmployee.id)

  def deletePocEmployee(employeeId: UUID): Task[Unit] =
    run(deleteEmployeeQuery(employeeId)).void

  def getByCertifyUserId(certifyUserId: UUID): Task[Option[PocEmployee]] =
    run(getByCertifyUserIdQuery(certifyUserId)).map(_.headOption)

}
