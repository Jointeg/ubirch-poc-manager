package com.ubirch.controllers

import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Presenter,
  Token
}
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.poc.employee.GetPocLogoError.{ LogoNotFoundError, PocNotFoundError }
import com.ubirch.services.poc.employee.PocEmployeeService
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.{ ActionResult, BadRequest, InternalServerError, NotFound, Ok, ScalatraBase }
import EndpointHelpers.retrieveEmployeeFromToken
import com.ubirch.db.tables.PocEmployeeRepository
import com.ubirch.models.NOK
import com.ubirch.services.poc.employee.GetCertifyConfigError.{ InvalidDataPocType, UnknownPoc }
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }

import java.util.UUID
import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

@Singleton
class PocEmployeeController @Inject() (
  val swagger: Swagger,
  config: Config,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  tokenVerificationService: TokenVerificationService,
  pocEmployeeService: PocEmployeeService,
  pocEmployeeRepository: PocEmployeeRepository)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, CertifyKeycloak, tokenVerificationService, publicKeyPoolService)

  override protected def applicationDescription: String = "Poc Employee Controller"

  implicit override protected def jsonFormats: Formats = jFormats

  override def service: String = config.getString(GenericConfPaths.NAME)

  override def successCounter: Counter = Counter
    .build()
    .name("poc_employee_success")
    .help("Represents the number of poc employee operation successes")
    .labelNames("service", "method")
    .register()

  override def errorCounter: Counter = Counter
    .build()
    .name("poc_employee_failures")
    .help("Represents the number of poc employee operation failures")
    .labelNames("service", "method")
    .register()

  val getCertifyConfig: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve certify config")
      .summary("Get Certify Config of Poc Employee")
      .description("Retrieve Certify Config queried by Poc Id. If it doesn't exist 404 is returned.")
      .tags("Tenant-Admin", "PoC-Employee")
      .authorizations()

  get("/certify-config", operation(getCertifyConfig)) {
    authenticated(_.hasRole(Token.POC_EMPLOYEE)) { token: Token =>
      asyncResult("Get Certify Config by PocEmployee") { _ => response =>
        retrieveEmployeeFromToken(token, pocEmployeeRepository) { employee =>
          pocEmployeeService.getCertifyConfig(employee).map {
            case Left(UnknownPoc(pocId)) =>
              logger.error(s"Could not find poc with id $pocId (assigned to ${employee.id} PocEmployee)")
              NotFound(NOK.resourceNotFoundError("Could not find Poc assigned to given PocEmployee"))
            case Left(InvalidDataPocType(pocType, pocId)) =>
              logger.error(
                s"Invalid pocType $pocType found in poc with id $pocId (assigned to ${employee.id} PocEmployee)")
              BadRequest(NOK.badRequest("Invalid data found in Poc assigned to given PocEmployee"))
            case Right(dto) =>
              response.contentType = Some("application/json")
              Presenter.toJsonResult(dto)
          }.onErrorHandle { ex =>
            logger.error("something went wrong retrieving certify config for poc employee" + ex.getMessage)
            InternalServerError(NOK.serverError(
              s"something went wrong retrieving certify config for poc employee with id ${employee.id}"))
          }
        }
      }
    }
  }

  val getPocLogo: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve poc config")
      .summary("Get Certify Config of Poc Employee")
      .description("Retrieve Certify Config queried by Poc Id. If it doesn't exist 404 is returned.")
      .tags("Tenant-Admin", "PoC-Employee", "Poc Config")

  //no authentication needed
  get("/logo/:pocId", operation(getPocLogo)) {
    asyncResult("Get poc logo by PocEmployee") { _ => _ =>
      getParamAsUUID("pocId", id => s"Could not convert provided pocId ($id) to UUID") { pocId =>
        pocEmployeeService.getPocLogo(pocId).map {
          case Right(logoResponse) =>
            response.setHeader("content-type", s"image/${logoResponse.fileExtension}")
            Ok(logoResponse.logo)
          case Left(PocNotFoundError(pocId)) =>
            logger.error(s"Could not find poc with id $pocId")
            NotFound(NOK.resourceNotFoundError("Could not find Poc assigned to given PocEmployee"))
          case Left(LogoNotFoundError(pocId)) =>
            logger.error(s"Could not find logo for poc $pocId")
            BadRequest(NOK.resourceNotFoundError("Could not find Logo assigned to given Poc"))
        }
      }.onErrorHandle { ex =>
        logger.error("something unexpected happened on logo retrieval ", ex)
        InternalServerError("GET /logo/:pocId failed unexpectedly.")
      }
    }
  }

  private def getParamAsUUID(paramName: String, errorMsg: String => String)(logic: UUID => Task[ActionResult]) = {
    val id = params(paramName)
    Try(UUID.fromString(id)) match {
      case Success(uuid) => logic(uuid)
      case Failure(ex) =>
        logger.error(errorMsg(id), ex)
        Task(BadRequest(NOK.badRequest(errorMsg + ex.getMessage)))
    }
  }
}
