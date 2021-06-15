package com.ubirch.services.pocadmin

import cats.data.EitherT
import cats.implicits.catsSyntaxEitherId
import com.ubirch.controllers.EndpointHelpers.ActivateSwitch
import com.ubirch.controllers.SwitchActiveError._
import com.ubirch.controllers.model.PocAdminControllerJsonModel.PocEmployee_IN
import com.ubirch.controllers.{ EndpointHelpers, SwitchActiveError }
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables.model.{ AdminCriteria, PaginatedResult }
import com.ubirch.db.tables.{ PocEmployeeRepository, PocRepository, TenantRepository }
import com.ubirch.models.poc.{ Completed, PocAdmin, Status }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.TenantId
import com.ubirch.models.user.{ Email, FirstName, LastName }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.{
  KeycloakUserService,
  Remove2faTokenKeycloakError,
  UpdateEmployeeKeycloakError
}
import com.ubirch.services.pocadmin.GetPocsAdminErrors.{ PocAdminNotInCompletedStatus, UnknownTenant }
import com.ubirch.util.PocAuditLogging
import monix.eval.Task
import org.keycloak.models.jpa.entities.UserFederationProviderEntity

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

  def getEmployeeForPocAdmin(
    pocAdmin: PocAdmin,
    pocEmployeeId: UUID): Task[Either[GetEmployeeForPocAdminError, PocEmployee]]

  def updateEmployee(
    pocAdmin: PocAdmin,
    id: UUID,
    value: PocEmployee_IN): Task[Either[UpdatePocEmployeeError, PocEmployee]]
}

@Singleton
class PocAdminServiceImpl @Inject() (
  employeeRepository: PocEmployeeRepository,
  tenantRepository: TenantRepository,
  keycloakUserService: KeycloakUserService,
  pocRepository: PocRepository,
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
    active: ActivateSwitch): Task[Either[SwitchActiveError, Unit]] =
    tenantRepository.getTenant(pocAdmin.tenantId).flatMap {
      case Some(tenant) =>
        quillMonixJdbcContext.withTransaction {
          employeeRepository
            .getPocEmployee(employeeId)
            .flatMap {
              case None                                               => Task(ResourceNotFound(employeeId).asLeft)
              case Some(employee) if employee.pocId != pocAdmin.pocId => Task(NotAllowedError.asLeft)
              case Some(employee) if employee.status != Completed     => Task(UserNotCompleted.asLeft)
              case Some(employee) if employee.certifyUserId.isEmpty   => Task(MissingCertifyUserId(employeeId).asLeft)

              case Some(employee) =>
                val userId = employee.certifyUserId.get

                (active match {
                  case EndpointHelpers.Activate =>
                    keycloakUserService.activate(tenant.getRealm, userId, CertifyKeycloak)
                  case EndpointHelpers.Deactivate =>
                    keycloakUserService.deactivate(tenant.getRealm, userId, CertifyKeycloak)
                }) >> employeeRepository.updatePocEmployee(employee.copy(active = ActivateSwitch.toBoolean(active)))
                  .map { _ =>
                    logAuditByPocAdmin(s"$active poc employee ${employee.id} of poc ${employee.pocId}.", pocAdmin)
                    Right(())
                  }
            }
        }
      case None => Task(ResourceNotFound(pocAdmin.tenantId.asUUID()).asLeft)
    }

  override def getEmployeeForPocAdmin(
    pocAdmin: PocAdmin,
    pocEmployeeId: UUID): Task[Either[GetEmployeeForPocAdminError, PocEmployee]] =
    for {
      maybePocEmployee <- employeeRepository.getPocEmployee(pocEmployeeId)
      r <- maybePocEmployee match {
        case None => Task.pure(GetEmployeeForPocAdminError.NotFound(pocEmployeeId).asLeft)
        case Some(pe) if pe.tenantId != pocAdmin.tenantId =>
          Task.pure(GetEmployeeForPocAdminError.DoesNotBelongToTenant(pe.id, pocAdmin.tenantId).asLeft)
        case Some(pe) => Task.pure(pe.asRight)
      }
    } yield r

  override def updateEmployee(
    pocAdmin: PocAdmin,
    pocEmployeeId: UUID,
    pocEmployeeIn: PocEmployee_IN): Task[Either[UpdatePocEmployeeError, PocEmployee]] =
    quillMonixJdbcContext.withTransaction {
      for {
        updatableEmployee <- getUpdatableEmployeeForAdmin(pocAdmin, pocEmployeeId)
        updated <- updatableEmployee match {
          case Left(e) => Task.pure(e.asLeft)
          case Right(pe) =>
            val updated = pocEmployeeIn.copyToPocEmployee(pe)
            for {
              poc <- pocRepository.single(pe.pocId)
              updatedEmployee = pocEmployeeIn.copyToPocEmployee(pe)
              _ <- employeeRepository.updatePocEmployee(updatedEmployee)
              updateKeycloak <- keycloakUserService.updateEmployee(
                poc.getRealm,
                pe,
                FirstName(pocEmployeeIn.firstName),
                LastName(pocEmployeeIn.lastName),
                Email(pocEmployeeIn.email))
              updateEmployee <- updateKeycloak match {
                case Left(e)  => Task.pure(UpdatePocEmployeeError.KeycloakError(e).asLeft)
                case Right(_) => Task.pure(updatedEmployee.asRight)
              }
            } yield updateEmployee
        }
      } yield updated
    }

  private def getUpdatableEmployeeForAdmin(
    pocAdmin: PocAdmin,
    pocEmployeeId: UUID): Task[Either[UpdatePocEmployeeError, PocEmployee]] =
    for {
      employee <- getEmployeeForPocAdmin(pocAdmin, pocEmployeeId)
      updatableEmployee <- employee match {
        case Right(pe) if pe.status != Completed =>
          Task.pure(UpdatePocEmployeeError.WrongStatus(pe.id, pe.status, Completed).asLeft)
        case Right(pe) => Task.pure(pe.asRight)
        case Left(e) => e match {
            case GetEmployeeForPocAdminError.NotFound(id) =>
              Task.pure(UpdatePocEmployeeError.NotFound(id).asLeft)
            case GetEmployeeForPocAdminError.DoesNotBelongToTenant(id, tenantId) =>
              Task.pure(UpdatePocEmployeeError.DoesNotBelongToTenant(id, tenantId).asLeft)
          }
      }
    } yield updatableEmployee
}

sealed trait GetPocsAdminErrors
object GetPocsAdminErrors {
  case class PocAdminNotInCompletedStatus(pocAdminId: UUID) extends GetPocsAdminErrors
  case class UnknownTenant(tenantId: TenantId) extends GetPocsAdminErrors
}

sealed trait GetEmployeeForPocAdminError
object GetEmployeeForPocAdminError {
  case class NotFound(id: UUID) extends GetEmployeeForPocAdminError
  case class DoesNotBelongToTenant(id: UUID, tenantId: TenantId) extends GetEmployeeForPocAdminError
}

sealed trait UpdatePocEmployeeError
object UpdatePocEmployeeError {
  case class NotFound(id: UUID) extends UpdatePocEmployeeError
  case class DoesNotBelongToTenant(id: UUID, tenantId: TenantId) extends UpdatePocEmployeeError
  case class WrongStatus(id: UUID, status: Status, expectedStatus: Status) extends UpdatePocEmployeeError
  case class KeycloakError(e: UpdateEmployeeKeycloakError) extends UpdatePocEmployeeError
}
