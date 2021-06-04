package com.ubirch.controllers.error

import com.ubirch.controllers.concerns.Presenter
import com.ubirch.models.NOK
import com.ubirch.models.poc.PocAdmin
import com.ubirch.services.tenantadmin.GetPocAdminForTenantError
import monix.eval.Task
import org.json4s.Formats
import org.scalatra.{ ActionResult, NotFound, Unauthorized }

class GetPocAdminForTenantErrorMapper(implicit f: Formats) extends ErrorMapper[GetPocAdminForTenantError, PocAdmin] {
  override def handle(t: Task[Either[GetPocAdminForTenantError, PocAdmin]]): Task[ActionResult] =
    t.map {
      case Left(e) => e match {
          case GetPocAdminForTenantError.NotFound(pocAdminId) =>
            NotFound(NOK.resourceNotFoundError(s"PoC Admin with id '$pocAdminId' does not exist"))
          case GetPocAdminForTenantError.AssignedToDifferentTenant(pocAdminId, tenantId) =>
            Unauthorized(NOK.authenticationError(
              s"PoC Admin with id '$pocAdminId' does not belong to tenant with id '${tenantId.value.value}'"))
        }
      case Right(p) => Presenter.toJsonResult(p)
    }
}
