package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.model.{ AdminCriteria, PaginatedResult }
import com.ubirch.models.common.Sort
import com.ubirch.models.poc.{ Completed, PocAdmin, Status }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.TenantId
import io.getquill._
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

trait PocEmployeeRepository {
  def createPocEmployee(employee: PocEmployee): Task[UUID]

  def updatePocEmployee(pocEmployee: PocEmployee): Task[UUID]

  def getPocEmployee(employeeId: UUID): Task[Option[PocEmployee]]

  def getPocEmployeesByTenantId(tenantId: TenantId): Task[List[PocEmployee]]

  def getUncompletedPocEmployeesIds(): Task[List[UUID]]

  def unsafeGetUncompletedPocEmployeeById(id: UUID): Task[PocEmployee]

  def deletePocEmployee(employeeId: UUID): Task[Unit]

  def getAllByCriteria(criteria: AdminCriteria): Task[PaginatedResult[PocEmployee]]

  def getByCertifyUserId(certifyUserId: UUID): Task[Option[PocEmployee]]
}

class PocEmployeeTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends PocEmployeeRepository {
  import QuillMonixJdbcContext._
  import ctx._

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

  private def getAllPocEmployeeIdsWithoutStatusQuery(status: Status): Quoted[EntityQuery[UUID]] =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").filter(_.status != lift(status)).map(_.id)
    }

  private def getPocEmployeeWithoutStatusById(status: Status, id: UUID) =
    quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").filter(emp =>
        emp.status != lift(status) && emp.id == lift(id))
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

  private def filterByStatuses(q: Quoted[Query[PocEmployee]], statuses: Seq[Status]) =
    statuses match {
      case Nil => quote(q)
      case _   => quote(q.filter(p => liftQuery(statuses).contains(p.status)))
    }

  private def getAllByCriteriaQuery(criteria: AdminCriteria): Quoted[Query[PocEmployee]] = {
    val employeeByPocId = quote {
      querySchema[PocEmployee]("poc_manager.poc_employee_table").join(
        querySchema[PocAdmin]("poc_manager.poc_admin_table")).on {
        case (pe, pa) => pe.pocId == pa.pocId
      }.filter {
        case (_, pa) => pa.id == lift(criteria.adminId)
      }.map(_._1)
    }

    criteria.search match {
      case Some(s) =>
        quote {
          employeeByPocId
            .filter(e => e.email.like(lift(s"$s%")) || e.name.like(lift(s"$s%")) || e.surname.like(lift(s"$s%")))
        }
      case None => employeeByPocId
    }
  }

  private def sortPocEmployees(q: Quoted[Query[PocEmployee]], sort: Sort): DynamicQuery[PocEmployee] = {
    val dynamic = q.dynamic
    sort.field match {
      case Some("firstName") => dynamic.sortBy(p => quote(p.name))(sort.ord)
      case Some("lastName")  => dynamic.sortBy(p => quote(p.surname))(sort.ord)
      case Some("email")     => dynamic.sortBy(p => quote(p.email))(sort.ord)
      case Some("status")    => dynamic.sortBy(p => quote(p.status))(sort.ord)
      case Some("active")    => dynamic.sortBy(p => quote(p.active))(sort.ord)
      case _                 => dynamic
    }
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

  def updatePocEmployee(pocEmployee: PocEmployee): Task[UUID] =
    run(updatePocEmployeeQuery(pocEmployee)).map(_ => pocEmployee.id)

  def deletePocEmployee(employeeId: UUID): Task[Unit] =
    run(deleteEmployeeQuery(employeeId)).void

  def getByCertifyUserId(certifyUserId: UUID): Task[Option[PocEmployee]] =
    run(getByCertifyUserIdQuery(certifyUserId)).map(_.headOption)

  def getAllByCriteria(criteria: AdminCriteria): Task[PaginatedResult[PocEmployee]] =
    transaction {
      val pocEmployeesByCriteria = filterByStatuses(getAllByCriteriaQuery(criteria), criteria.filter.status)
      val sortedEmployees = sortPocEmployees(pocEmployeesByCriteria, criteria.sort)
      for {
        total <- run(pocEmployeesByCriteria.size)
        employees <- run {
          sortedEmployees
            .drop(quote(lift(criteria.page.index * criteria.page.size)))
            .take(quote(lift(criteria.page.size)))
        }
      } yield {
        PaginatedResult(total, employees)
      }
    }

  override def getUncompletedPocEmployeesIds(): Task[List[UUID]] =
    run(getAllPocEmployeeIdsWithoutStatusQuery(Completed))

  override def unsafeGetUncompletedPocEmployeeById(id: UUID): Task[PocEmployee] =
    run(getPocEmployeeWithoutStatusById(Completed, id)).map(_.head)
}
