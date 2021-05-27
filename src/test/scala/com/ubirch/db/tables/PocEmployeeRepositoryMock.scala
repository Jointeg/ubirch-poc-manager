package com.ubirch.db.tables
import com.ubirch.models.poc.Completed
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.TenantId
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocEmployeeRepositoryMock extends PocEmployeeRepository {

  private val pocEmployeeDatastore = mutable.Map[UUID, PocEmployee]()

  def createPocEmployee(pocEmployee: PocEmployee): Task[UUID] = {
    Task {
      pocEmployeeDatastore += (pocEmployee.id -> pocEmployee)
      pocEmployee.id
    }
  }

  def updatePocEmployee(pocEmployee: PocEmployee): Task[UUID] = {
    Task {
      pocEmployeeDatastore.update(pocEmployee.id, pocEmployee)
      pocEmployee.id
    }
  }

  def getPocEmployee(pocEmployeeId: UUID): Task[Option[PocEmployee]] = {
    Task(pocEmployeeDatastore.get(pocEmployeeId))
  }

  def getAllUncompletedPocEmployees: Task[List[PocEmployee]] =
    Task {
      pocEmployeeDatastore.collect {
        case (_, pocEmployee: PocEmployee) if pocEmployee.status != Completed => pocEmployee
      }.toList
    }

  override def getPocEmployeesByTenantId(tenantId: TenantId): Task[List[PocEmployee]] =
    Task {
      pocEmployeeDatastore.collect {
        case (_, pocEmployee: PocEmployee) if pocEmployee.tenantId == tenantId => pocEmployee
      }.toList
    }

  override def getUncompletedPocEmployees(): Task[List[PocEmployee]] =
    Task {
      pocEmployeeDatastore.collect {
        case (_, employee: PocEmployee) if employee.status != Completed => employee
      }.toList
    }

  override def deletePocEmployee(employeeId: UUID): Task[Unit] =
    Task(pocEmployeeDatastore.remove(employeeId))
}