package com.ubirch.controllers.error

import com.ubirch.controllers.concerns.Presenter
import com.ubirch.models.NOK
import com.ubirch.models.poc.Poc
import com.ubirch.services.tenantadmin.GetPocForTenantError
import monix.eval.Task
import org.json4s.Formats
import org.scalatra.{ ActionResult, NotFound, Unauthorized }

class GetPocForTenantErrorMapper(implicit f: Formats) extends ErrorMapper[GetPocForTenantError, Poc] {
  override def handle(t: Task[Either[GetPocForTenantError, Poc]]): Task[ActionResult] =
    t.map {
      case Left(e) => e match {
          case GetPocForTenantError.NotFound(pocId) =>
            NotFound(NOK.resourceNotFoundError(s"PoC with id '$pocId' does not exist"))
          case GetPocForTenantError.AssignedToDifferentTenant(pocId, tenantId) =>
            Unauthorized(NOK.authenticationError(
              s"PoC with id '$pocId' does not belong to tenant with id '${tenantId.value.value}'"))
        }
      case Right(p) => Presenter.toJsonResult(p)
    }
}
