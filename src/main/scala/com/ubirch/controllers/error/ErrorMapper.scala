package com.ubirch.controllers.error

import com.ubirch.models.poc.Poc
import com.ubirch.services.tenantadmin.{ GetPocForTenantError, UpdatePocError }
import monix.eval.Task
import org.json4s.Formats
import org.scalatra.ActionResult

import scala.language.higherKinds

trait ErrorMapper[E, T] {
  def handle(t: Task[Either[E, T]]): Task[ActionResult]
}

object ErrorMapper {
  implicit class ErrorMapperOnTaskOps[E, T](t: Task[Either[E, T]]) {
    def toActionResult(implicit em: ErrorMapper[E, T]): Task[ActionResult] = em.handle(t)
  }

  implicit def getPocForTenantErrorMapper(implicit f: Formats): ErrorMapper[GetPocForTenantError, Poc] =
    new GetPocForTenantErrorMapper

  implicit def updatePocErrorMapper(implicit f: Formats): ErrorMapper[UpdatePocError, Unit] =
    new UpdatePocErrorMapper
}
