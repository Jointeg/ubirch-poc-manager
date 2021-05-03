package com.ubirch.controllers

import com.google.inject.Singleton
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Token
}
import com.ubirch.models.NOK
import com.ubirch.models.tenant.{ API, APP, Both, CreateTenantRequest }
import com.ubirch.services.DeviceKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.superadmin.TenantService
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }
import org.scalatra.{ InternalServerError, NotFound, Ok, ScalatraBase }

import javax.inject.Inject
import scala.concurrent.ExecutionContext

@Singleton
class SuperAdminController @Inject() (
  config: Config,
  val swagger: Swagger,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  tokenVerificationService: TokenVerificationService,
  tenantService: TenantService)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {
  override protected val applicationDescription: String = "Super Admin controller"

  implicit override protected def jsonFormats: Formats = jFormats

  override val service: String = config.getString(GenericConfPaths.NAME)

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, DeviceKeycloak, tokenVerificationService, publicKeyPoolService)

  override val successCounter: Counter =
    Counter
      .build()
      .name("super_admin_success")
      .help("Represents the number of super admin operation successes")
      .labelNames("service", "method")
      .register()

  override val errorCounter: Counter = Counter
    .build()
    .name("super_admin_failures")
    .help("Represents the number of super admin operation failures")
    .labelNames("service", "method")
    .register()

  private val createTenantOperation: SwaggerSupportSyntax.OperationBuilder = {
    apiOperation[String]("CreateTenant")
      .summary("Creates a Tenant")
      .description("Function that will be used by SuperAdmin users in order to create Tenants")
      .tags("SuperAdmin")
      .authorizations()
      .parameters(
        bodyParam[String]("tenantName").description("Name of Tenant"),
        bodyParam[String]("usageType")
          .description("Describes channel through which POC will be managed")
          .allowableValues(List(API, APP, Both)),
        bodyParam[String]("deviceCreationToken"),
        bodyParam[String]("certificationCreationToken"),
        bodyParam[String]("idGardIdentifier"),
        bodyParam[String]("tenantGroupId")
      )
  }

  post("/tenants/create", operation(createTenantOperation)) {
    authenticated(_.hasRole(Token.SUPER_ADMIN)) { _ =>
      asyncResult("CreateTenant") { _ => _ =>
        tenantService
          .createTenant(parsedBody.extract[CreateTenantRequest])
          .map(_ => Ok())
          .onErrorHandle { ex: Throwable =>
            val errorMsg = s"failure on tenant creation"
            logger.error(errorMsg, ex)
            InternalServerError(NOK.serverError(errorMsg + ex.getMessage))
          }
      }
    }
  }

  notFound {
    asyncResult("not_found") { _ => _ =>
      Task {
        logger.info(
          "controller=SuperAdminController route_not_found={} query_string={}",
          requestPath,
          request.getQueryString)
        NotFound(NOK.noRouteFound(requestPath + " might exist in another universe"))
      }
    }
  }

  before() {
    contentType = "application/json"
  }
}
