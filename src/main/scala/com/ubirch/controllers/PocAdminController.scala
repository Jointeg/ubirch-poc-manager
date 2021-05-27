package com.ubirch.controllers
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.EndpointHelpers._
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Token
}
import com.ubirch.db.tables.PocAdminRepository
import com.ubirch.models.NOK
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.poc.employee.{
  CsvContainedErrors,
  CsvProcessPocEmployee,
  EmptyCSVError,
  HeaderParsingError,
  PocAdminNotInCompletedStatus,
  UnknownCsvParsingError,
  UnknownTenant
}
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }
import org.scalatra.{ ActionResult, BadRequest, InternalServerError, NotFound, Ok, ScalatraBase }

import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext

@Singleton
class PocAdminController @Inject() (
  val swagger: Swagger,
  config: Config,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  pocAdminRepository: PocAdminRepository,
  csvProcessPocEmployee: CsvProcessPocEmployee,
  tokenVerificationService: TokenVerificationService
)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, CertifyKeycloak, tokenVerificationService, publicKeyPoolService)

  override protected def applicationDescription: String = "PoC Admin Controller"

  implicit override protected def jsonFormats: Formats = jFormats

  override def service: String = config.getString(GenericConfPaths.NAME)

  override def successCounter: Counter = Counter
    .build()
    .name("poc_admin_success")
    .help("Represents the number of poc admin operation successes")
    .labelNames("service", "method")
    .register()

  override def errorCounter: Counter = Counter
    .build()
    .name("poc_admin_failures")
    .help("Represents the number of poc admin operation failures")
    .labelNames("service", "method")
    .register()

  val createListOfEmployees: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("create list of employees")
      .summary("PoC employee batch creation")
      .description("Receives a semicolon separated .csv with a list of PoC employees." +
        " In case of not parsable rows, these will be returned in the answer with a specific remark.")
      .tags("PoC", "PoC-Admin", "PoC-Employee")

  post("/employee/create", operation(createListOfEmployees)) {
    pocAdminEndpoint("Create employees by CSV file") { token =>
      retrievePocAdminFromToken(token, pocAdminRepository) { pocAdmin =>
        csvProcessPocEmployee.createListOfPocEmployees(request.body, pocAdmin).map {
          case Left(UnknownTenant(tenantId)) =>
            logger.error(s"Could not find tenant with id $tenantId (assigned to ${pocAdmin.id} PocAdmin)")
            NotFound(NOK.resourceNotFoundError("Could not find tenant assigned to given PocAdmin"))
          case Left(PocAdminNotInCompletedStatus(pocAdminId)) =>
            logger.error(s"Could not create employees because PocAdmin is not in completed state: $pocAdminId")
            BadRequest(NOK.badRequest("PoC Admin is not fully setup"))
          case Left(HeaderParsingError(msg)) =>
            logger.error(s"Error has occurred during header parsing: $msg sent by ${pocAdmin.id}")
            BadRequest(NOK.badRequest("Header in CSV file is not correct"))
          case Left(EmptyCSVError(msg)) =>
            logger.error(s"Empty CSV file received from ${pocAdmin.id} PoC Admin: $msg")
            BadRequest(NOK.badRequest("Empty CSV body"))
          case Left(UnknownCsvParsingError(msg)) =>
            logger.error(s"Unexpected error has occurred while parsing the CSV: $msg")
            InternalServerError(NOK.serverError("Unknown error has happened while parsing the CSV file"))
          case Left(CsvContainedErrors(errors)) => Ok(errors)
          case Right(_)                         => Ok()
        }
      }
    }
  }

  private def pocAdminEndpoint(description: String)(logic: Token => Task[ActionResult]) = {
    authenticated(_.hasRole(Token.POC_ADMIN)) { token: Token =>
      asyncResult(description) { _ => _ => logic(token) }
    }
  }
}