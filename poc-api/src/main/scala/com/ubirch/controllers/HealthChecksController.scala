package com.ubirch.controllers

import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.ControllerBase
import com.ubirch.services.healthcheck.{ HealthCheckService, NotOperational, Operational }
import io.prometheus.client.Counter
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.Swagger
import org.scalatra.{ Ok, ServiceUnavailable }

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class HealthChecksController @Inject() (
  config: Config,
  val swagger: Swagger,
  jFormats: Formats,
  healthCheckService: HealthCheckService
)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase {

  override def service: String = config.getString(GenericConfPaths.NAME)

  override def successCounter: Counter = Counter
    .build()
    .name("health_checks_success")
    .help("Represents the number of health checks controller successes")
    .labelNames("service", "method")
    .register()
  override def errorCounter: Counter = Counter
    .build()
    .name("health_checks_failures")
    .help("Represents the number of health checks controller failures")
    .labelNames("service", "method")
    .register()
  override protected def applicationDescription: String = "Health Checks Controller"
  implicit override protected def jsonFormats: Formats = jFormats

  get("/liveness") {
    healthCheckService.performAllHealthChecks().map {
      case NotOperational => ServiceUnavailable()
      case Operational    => Ok()
    }.runToFuture
  }

  get("/readiness") {
    Ok()
  }

}
