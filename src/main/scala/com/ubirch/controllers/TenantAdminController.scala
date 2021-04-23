package com.ubirch.controllers

import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.{ControllerBase, KeycloakBearerAuthStrategy, KeycloakBearerAuthenticationSupport}
import com.ubirch.services.jwt.{PublicKeyPoolService, TokenVerificationService}
import com.ubirch.services.poc.PocBatchHandlerImpl
import io.prometheus.client.Counter
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.{Swagger, SwaggerSupportSyntax}
import org.scalatra.{Ok, ScalatraBase}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TenantAdminController @Inject()(
                                       pocCreator: PocBatchHandlerImpl,
                                       config: Config,
                                       val swagger: Swagger,
                                       jFormats: Formats,
                                       publicKeyPoolService: PublicKeyPoolService,
                                       tokenVerificationService: TokenVerificationService)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
    with KeycloakBearerAuthenticationSupport {

  implicit override protected def jsonFormats: Formats = jFormats

  override protected def applicationDescription: String = "Tenant Admin Controller"

  override val service: String = config.getString(GenericConfPaths.NAME)

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, tokenVerificationService, publicKeyPoolService)

  override val successCounter: Counter =
    Counter
      .build()
      .name("tenant_admin_success")
      .help("Represents the number of tenant admin controller successes")
      .labelNames("service", "method")
      .register()

  override val errorCounter: Counter = Counter
    .build()
    .name("tenant_admin_failures")
    .help("Represents the number of tenant admin controller failures")
    .labelNames("service", "method")
    .register()

  val createListOfPocs: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("create list of PoCs")
      .summary("PoC batch creation")
      .description("Receives a semicolon separated .csv with a list of PoC details to create the PoCs." +
        " In case of not parsable rows, these will be returned in the answer with a specific remark.")
      .tags("PoC, Tenant-Admin")

  post("/pocs/create", operation(createListOfPocs)) {
    authenticated() { token =>
      asyncResult("Create poc batch") { _ =>
        _ =>

          pocCreator
            .createListOfPoCs(request.body)
            .map {
              case Right(_) => Ok()
              case Left(csv) => Ok(csv)
            }
      }
    }
  }

}
