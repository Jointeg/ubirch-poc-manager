package com.ubirch.controllers

import com.google.inject.Singleton
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.ControllerBase
import com.ubirch.models.NOK
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.{NotFound, Ok}
import org.scalatra.swagger.{Swagger, SwaggerSupportSyntax}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

@Singleton
class SuperAdminController @Inject()(config: Config, val swagger: Swagger, jFormats: Formats)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase {
  override protected val applicationDescription: String = "Super Admin controller"

  override protected implicit def jsonFormats: Formats = jFormats

  override val service: String = config.getString(GenericConfPaths.NAME)

  override val successCounter: Counter =
    Counter.build()
      .name("super_admin_success")
      .help("Represents the number of super admin operation successes")
      .labelNames("service", "method")
      .register()

  override val errorCounter: Counter = Counter.build()
    .name("super_admin_failures")
    .help("Represents the number of super admin operation failures")
    .labelNames("service", "method")
    .register()

  val getSimpleCheck: SwaggerSupportSyntax.OperationBuilder =
    (apiOperation[String]("initialTest")
      summary "Test"
      description "Getting a test message from system"
      tags "Test")

  get("/initialTest", operation(getSimpleCheck)) {
    asyncResult("initialTest") { _ =>
      _ =>
        Task(test())
    }
  }

  notFound {
    asyncResult("not_found") { _ => _ =>
      Task {
        logger.info("controller=SuperAdminController route_not_found={} query_string={}", requestPath, request.getQueryString)
        NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
      }
    }
  }

  private def test() = {
    Ok("Test successful")
  }
}
