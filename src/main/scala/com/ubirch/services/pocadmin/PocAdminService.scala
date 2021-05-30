package com.ubirch.services.pocadmin

import cats.data.EitherT
import cats.implicits.catsSyntaxEitherId
import com.ubirch.controllers.EndpointHelpers.ActivateSwitch
import com.ubirch.controllers.SwitchActiveError.{
  MissingCertifyUserId,
  NotAllowedError,
  UserNotCompleted,
  UserNotFound
}
import com.ubirch.controllers.{ EndpointHelpers, SwitchActiveError }
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.model.{ AdminCriteria, PaginatedResult }
import com.ubirch.db.tables.{ PocEmployeeRepository, TenantRepository }
import com.ubirch.models.poc.{ Completed, PocAdmin }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.TenantId
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.pocadmin.GetPocsAdminErrors.{ PocAdminNotInCompletedStatus, UnknownTenant }
import com.ubirch.util.PocAuditLogging
import monix.eval.Task

import java.util.UUID
import javax.inject.{ Inject, Singleton }

trait PocAdminService {
  def getEmployees(
    pocAdmin: PocAdmin,
    criteria: AdminCriteria): Task[Either[GetPocsAdminErrors, PaginatedResult[PocEmployee]]]

  def switchActiveForPocEmployee(
    employeeId: UUID,
    pocAdmin: PocAdmin,
    active: ActivateSwitch): Task[Either[SwitchActiveError, Unit]]

}

@Singleton
class PocAdminServiceImpl @Inject() (
  employeeRepository: PocEmployeeRepository,
  tenantRepository: TenantRepository,
  keycloakUserService: KeycloakUserService,
  quillMonixJdbcContext: QuillMonixJdbcContext)
  extends PocAdminService
  with PocAuditLogging {

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

  override def switchActiveForPocEmployee(
    employeeId: UUID,
    pocAdmin: PocAdmin,
    active: ActivateSwitch): Task[Either[SwitchActiveError, Unit]] = {

    quillMonixJdbcContext.withTransaction {
      employeeRepository
        .getPocEmployee(employeeId)
        .flatMap {
          case None                                               => Task(UserNotFound(employeeId).asLeft)
          case Some(employee) if employee.pocId != pocAdmin.pocId => Task(NotAllowedError.asLeft)
          case Some(employee) if employee.status != Completed     => Task(UserNotCompleted.asLeft)
          case Some(employee) if employee.certifyUserId.isEmpty   => Task(MissingCertifyUserId(employeeId).asLeft)

          case Some(employee) =>
            val userId = employee.certifyUserId.get

            (active match {
              case EndpointHelpers.Activate   => keycloakUserService.activate(userId, CertifyKeycloak)
              case EndpointHelpers.Deactivate => keycloakUserService.deactivate(userId, CertifyKeycloak)
            }) >> employeeRepository.updatePocEmployee(employee.copy(active = ActivateSwitch.toBoolean(active)))
              .map { _ =>
                logAuditByPocAdmin(s"$active poc employee ${employee.id} of poc ${employee.pocId}.", pocAdmin)
                Right(())
              }
        }
    }
  }
}

sealed trait GetPocsAdminErrors
object GetPocsAdminErrors {
  case class PocAdminNotInCompletedStatus(pocAdminId: UUID) extends GetPocsAdminErrors
  case class UnknownTenant(tenantId: TenantId) extends GetPocsAdminErrors
}
