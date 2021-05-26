package com.ubirch.controllers
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.controllers.concerns.Token
import com.ubirch.db.tables.{ PocAdminRepository, TenantRepository }
import com.ubirch.models.NOK
import com.ubirch.models.poc.PocAdmin
import com.ubirch.models.tenant.{ Tenant, TenantName }
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.eval.Task
import org.scalatra.{ ActionResult, BadRequest, NotFound }

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
        }
    }
  }
}
