package com.ubirch.services.poc

import cats.data.EitherT
import com.ubirch.db.tables.{ PocEmployeeRepository, TenantRepository }
import com.ubirch.db.tables.model.{ AdminCriteria, PaginatedResult }
import com.ubirch.models.poc.{ Completed, PocAdmin }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.{ Tenant, TenantId }
import com.ubirch.services.poc.GetPocsAdminErrors.{ PocAdminNotInCompletedStatus, UnknownTenant }
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }

trait PocAdminService {
  def getEmployees(
    pocAdmin: PocAdmin,
    criteria: AdminCriteria): Task[Either[GetPocsAdminErrors, PaginatedResult[PocEmployee]]]
}

@Singleton
class PocAdminServiceImpl @Inject() (employeeRepository: PocEmployeeRepository, tenantRepository: TenantRepository)
  extends PocAdminService {

  override def getEmployees(
    pocAdmin: PocAdmin,
    criteria: AdminCriteria): Task[Either[GetPocsAdminErrors, PaginatedResult[PocEmployee]]] = {
    (for {
      _ <- verifyPocAdminStatus(pocAdmin)
      _ <- EitherT.fromOptionF(
        tenantRepository.getTenant(pocAdmin.tenantId),
        UnknownTenant(pocAdmin.tenantId))
      employees <- EitherT.liftF[Task, GetPocsAdminErrors, PaginatedResult[PocEmployee]](
        employeeRepository.getAllByCriteria(criteria))
    } yield employees).value
  }

  private def verifyPocAdminStatus(pocAdmin: PocAdmin): EitherT[Task, GetPocsAdminErrors, Unit] = {
    if (pocAdmin.status == Completed) {
      EitherT.rightT[Task, GetPocsAdminErrors](())
    } else {
      EitherT.leftT[Task, Unit](PocAdminNotInCompletedStatus(pocAdmin.id))
    }
  }

}

sealed trait GetPocsAdminErrors
object GetPocsAdminErrors {
  case class PocAdminNotInCompletedStatus(pocAdminId: UUID) extends GetPocsAdminErrors
  case class UnknownTenant(tenantId: TenantId) extends GetPocsAdminErrors
}
