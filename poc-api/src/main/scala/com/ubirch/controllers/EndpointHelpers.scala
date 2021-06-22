package com.ubirch.controllers

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.controllers.concerns.Token
import com.ubirch.db.tables.{ PocAdminRepository, PocEmployeeRepository, TenantRepository }
import com.ubirch.models.NOK
import com.ubirch.models.poc.PocAdmin
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.{ Tenant, TenantName }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.eval.Task
import org.scalatra.{ ActionResult, BadRequest, InternalServerError, NotFound }

import java.util.UUID
import scala.util.{ Either, Failure, Left, Right, Success }

object EndpointHelpers extends LazyLogging {

  def retrieveTenantFromToken(token: Token)(tenantRepository: TenantRepository): Task[Either[String, Tenant]] = {
    token.roles.find(_.name.startsWith(TENANT_GROUP_PREFIX)) match {
      case Some(roleName) =>
        tenantRepository.getTenantByName(TenantName(roleName.name.stripPrefix(TENANT_GROUP_PREFIX))).map {
          case Some(tenant) => Right(tenant)
          case None         => Left(s"couldn't find tenant in db for ${roleName.name}")
        }
      case None => Task(Left("the user's token is missing a tenant role"))
    }
  }

  def retrievePocAdminFromToken(
    token: Token,
    pocAdminRepository: PocAdminRepository)(logic: PocAdmin => Task[ActionResult]): Task[ActionResult] = {
    token.ownerIdAsUUID match {
      case Failure(_) => Task(BadRequest(NOK.badRequest("Owner ID in token is not in UUID format")))
      case Success(uuid) =>
        pocAdminRepository.getByCertifyUserId(uuid).flatMap {
          case Some(pocAdmin) => logic(pocAdmin)
          case None =>
            logger.error(s"Could not find user with CertifyID: $uuid")
            Task(NotFound(NOK.resourceNotFoundError("Could not find user with provided ID")))
        }.onErrorHandle { ex =>
          logger.error("something went wrong" + ex.getMessage)
          InternalServerError(NOK.serverError(
            "sorry, something went wrong."))
        }
    }
  }

  def retrieveEmployeeFromToken(
    token: Token,
    employeeRepository: PocEmployeeRepository)(logic: PocEmployee => Task[ActionResult]): Task[ActionResult] = {
    token.ownerIdAsUUID match {
      case Failure(_) => Task(BadRequest(NOK.badRequest("Owner ID in token is not in UUID format")))
      case Success(uuid) =>
        employeeRepository.getByCertifyUserId(uuid).flatMap {
          case Some(employee) => logic(employee)
          case None =>
            logger.error(s"Could not find user with CertifyID: $uuid")
            Task(NotFound(NOK.resourceNotFoundError("Could not find user with provided ID")))
        }.onErrorHandle { ex =>
          logger.error("something went wrong" + ex.getMessage)
          InternalServerError(NOK.serverError(
            "sorry, something went wrong."))
        }
    }
  }

  sealed trait ActivateSwitch

  object ActivateSwitch {
    def fromIntUnsafe(activate: Int): ActivateSwitch = {
      activate match {
        case 0 => Deactivate
        case 1 => Activate
        case _ => throw IllegalValueForActivateSwitch(activate)
      }
    }

    def toBoolean(activateSwitch: ActivateSwitch): Boolean =
      activateSwitch match {
        case Activate   => true
        case Deactivate => false
      }
  }

  case object Activate extends ActivateSwitch {
    override def toString: String = "activated"
  }

  case object Deactivate extends ActivateSwitch {
    override def toString: String = "deactivated"
  }

  case class IllegalValueForActivateSwitch(value: Int)
    extends IllegalArgumentException(s"Illegal value for ActivateSwitch: $value. Expected 0 or 1")
}

trait UserContext { val userId: UUID }
case class SuperAdminContext(userId: UUID)
case class TenantAdminContext(userId: UUID, tenantId: UUID) extends UserContext

sealed trait SwitchActiveError
object SwitchActiveError {
  case class ResourceNotFound(id: UUID) extends SwitchActiveError
  object UserNotCompleted extends SwitchActiveError
  object NotAllowedError extends SwitchActiveError
  case class MissingCertifyUserId(id: UUID) extends SwitchActiveError
}
