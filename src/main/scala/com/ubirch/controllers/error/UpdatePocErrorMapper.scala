package com.ubirch.controllers.error

import com.ubirch.controllers.concerns.Presenter
import com.ubirch.models.NOK
import com.ubirch.models.poc.Completed
import com.ubirch.services.tenantadmin.UpdatePocError
import monix.eval.Task
import org.json4s.Formats
import org.scalatra.{ ActionResult, Conflict, NotFound, Unauthorized }

class UpdatePocErrorMapper(implicit f: Formats) extends ErrorMapper[UpdatePocError, Unit] {
  override def handle(t: Task[Either[UpdatePocError, Unit]]): Task[ActionResult] =
    t.map {
      case Left(e) => e match {
          case UpdatePocError.NotFound(pocId) =>
            NotFound(NOK.resourceNotFoundError(s"PoC with id '$pocId' does not exist"))
          case UpdatePocError.AssignedToDifferentTenant(pocId, tenantId) =>
            Unauthorized(NOK.authenticationError(
              s"PoC with id '$pocId' does not belong to tenant with id '${tenantId.value.value}'"))
          case UpdatePocError.NotCompleted(pocId, status) =>
            Conflict(NOK.conflict(s"Poc '$pocId' is in wrong status: '$status', required: '$Completed'"))
        }
      case Right(p) => Presenter.toJsonResult(p)
    }
}
