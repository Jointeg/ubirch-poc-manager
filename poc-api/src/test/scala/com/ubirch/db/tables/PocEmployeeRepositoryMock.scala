package com.ubirch.db.tables

import com.ubirch.db.tables.model.{ AdminCriteria, PaginatedResult }
import com.ubirch.models.poc.{ Aborted, Completed }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.TenantId
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }
import scala.collection.mutable

@Singleton
class PocEmployeeRepositoryMock @Inject() (pocAdminRepository: PocAdminRepository) extends PocEmployeeRepository {

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

  override def getPocEmployeesByTenantId(tenantId: TenantId): Task[List[PocEmployee]] =
    Task {
      pocEmployeeDatastore.collect {
        case (_, pocEmployee: PocEmployee) if pocEmployee.tenantId == tenantId => pocEmployee
      }.toList
    }

  private def getUncompletedPocEmployees(): Task[List[PocEmployee]] =
    Task {
      pocEmployeeDatastore.collect {
        case (_, employee: PocEmployee) if employee.status != Completed && employee.status != Aborted => employee
      }.toList
    }

  override def deletePocEmployee(employeeId: UUID): Task[Unit] =
    Task(pocEmployeeDatastore.remove(employeeId))

  override def getByCertifyUserId(certifyUserId: UUID): Task[Option[PocEmployee]] =
    Task {
      pocEmployeeDatastore.collectFirst {
        case (_, pocEmployee: PocEmployee) if pocEmployee.certifyUserId.contains(certifyUserId) => pocEmployee
      }
    }

  override def getAllByCriteria(criteria: AdminCriteria): Task[PaginatedResult[PocEmployee]] = {
    pocAdminRepository.getPocAdmin(criteria.adminId).map {
      case None => PaginatedResult(0, Seq.empty[PocEmployee])
      case Some(pocAdmin) =>
        val employees = pocEmployeeDatastore.values
          .filter(_.pocId == pocAdmin.pocId)
          .filter(e => criteria.filter.status.contains(e.status))
        PaginatedResult(employees.size, employees.toSeq)
    }
  }

  override def getAllPocEmployeesToBecomeProcessed(): Task[List[UUID]] = getUncompletedPocEmployees().map(_.map(_.id))

  override def unsafeGetUncompletedPocEmployeeById(id: UUID): Task[PocEmployee] =
    getUncompletedPocEmployees().map(_.find(_.id == id).head)

  override def incrementCreationAttempts(id: UUID): Task[Unit] = getPocEmployee(id).flatMap {
    case Some(employee) => updatePocEmployee(employee.copy(creationAttempts = employee.creationAttempts + 1)).void
    case None           => Task.raiseError(new RuntimeException(s"Could not find PoC Employee with ID: $id"))
  }
}
