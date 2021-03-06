package com.ubirch.controllers

import com.google.inject.Singleton
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Token,
  X509CertSupport
}
import com.ubirch.models.NOK
import com.ubirch.models.tenant.{ API, APP, Both, CreateTenantRequest }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.superadmin.{ SuperAdminService, TenantCreationException }
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }
import org.scalatra._

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

@Singleton
class SuperAdminController @Inject() (
  config: Config,
  val swagger: Swagger,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  tokenVerificationService: TokenVerificationService,
  x509CertSupport: X509CertSupport,
  superAdminService: SuperAdminService)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {
  override protected val applicationDescription: String = "Super Admin controller"

  implicit override protected def jsonFormats: Formats = jFormats

  override val service: String = config.getString(GenericConfPaths.NAME)

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, CertifyKeycloak, tokenVerificationService, publicKeyPoolService)

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
        bodyParam[String]("idGardIdentifier"),
        bodyParam[String]("certifyGroupId"),
        bodyParam[String]("deviceGroupId"),
        bodyParam[String]("clientCert")
      )
  }

  post("/tenants/create", operation(createTenantOperation)) {
    x509CertSupport.withVerification(request) {
      superAdminEndpointWithUserContext("Create tenants") { superAdminContext =>
        superAdminService
          .createTenant(parsedBody.extract[CreateTenantRequest], superAdminContext)
          .map {
            case Left(error) =>
              logger.error(s"Could not create a tenant because: $error")
              InternalServerError(NOK.serverError("Failure during tenant creation"))
            case Right(_) => Ok()
          }
          .onErrorHandle {
            case TenantCreationException(errorMsg) =>
              InternalServerError(NOK.serverError(s"Failure during tenant creation: $errorMsg"))

            case ex: Throwable =>
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

  private def superAdminEndpointWithUserContext(description: String)(logic: SuperAdminContext => Task[ActionResult]) = {
    authenticated(_.hasRole(Token.SUPER_ADMIN)) { token: Token =>
      asyncResult(description) { _ => _ =>
        token.ownerIdAsUUID match {
          case Success(userId) =>
            logic(SuperAdminContext(userId))
          case Failure(uuid) =>
            Task(BadRequest(NOK.badRequest(s"Owner ID $uuid in token is not in UUID format")))
        }
      }
    }
  }

}
