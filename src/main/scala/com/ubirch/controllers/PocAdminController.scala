package com.ubirch.controllers
import cats.data.Validated
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.EndpointHelpers._
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Presenter,
  Token
}
import com.ubirch.controllers.validator.AdminCriteriaValidator
import com.ubirch.db.tables.PocAdminRepository
import com.ubirch.db.tables.model.AdminCriteria
import com.ubirch.models.{ NOK, Paginated_OUT, ValidationError, ValidationErrorsResponse }
import com.ubirch.models.poc.PocAdmin
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.poc.PocAdminService
import com.ubirch.services.poc.employee.{
  CsvContainedErrors,
  CsvProcessPocEmployee,
  EmptyCSVError,
  HeaderParsingError,
  PocAdminNotInCompletedStatus,
  UnknownCsvParsingError,
  UnknownTenant
}
import com.ubirch.services.poc.GetPocsAdminErrors
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
  tokenVerificationService: TokenVerificationService,
  pocAdminService: PocAdminService
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
      .authorizations()

  val getEmployees: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve all employees of the requesting employee")
      .summary("Get Employees")
      .description("Retrieve Employees that belong to the querying employee.")
      .tags("PoCs", "PoC Admins", "Poc-Employee")
      .authorizations()

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

  get("/employees", operation(getEmployees)) {
    pocAdminEndpoint("get employees") { token =>
      retrievePocAdminFromToken(token, pocAdminRepository) { pocAdmin =>
        (for {
          adminCriteria <- handleValidation(pocAdmin, AdminCriteriaValidator.validSortColumnsForEmployees)
          employees <- pocAdminService.getEmployees(pocAdmin, adminCriteria)
        } yield employees).map {
          case Right(employees) => Presenter.toJsonResult(Paginated_OUT(employees.total, employees.records))
          case Left(GetPocsAdminErrors.PocAdminNotInCompletedStatus(pocAdminId)) =>
            BadRequest(NOK.badRequest(s"PocAdmin status is not completed. $pocAdminId"))
          case Left(GetPocsAdminErrors.UnknownTenant(tenantId)) =>
            logger.error(s"Could not find tenant with id $tenantId (assigned to ${pocAdmin.id} PocAdmin)")
            NotFound(NOK.resourceNotFoundError("Could not find tenant assigned to given PocAdmin"))
        }.onErrorRecoverWith {
          case ValidationError(e) =>
            Presenter.toJsonStr(ValidationErrorsResponse(e.toNonEmptyList.toList.toMap))
              .map(BadRequest(_))
        }.onErrorHandle { ex =>
            InternalServerError(NOK.serverError(
              s"something went wrong retrieving employees for admin with id ${pocAdmin.id}" + ex.getMessage))
        }
      }
    }
  }

  private def pocAdminEndpoint(description: String)(logic: Token => Task[ActionResult]) = {
    authenticated(_.hasRole(Token.POC_ADMIN)) { token: Token =>
      asyncResult(description) { _ => _ => logic(token) }
    }
  }

  private def handleValidation(pocAdmin: PocAdmin, validSortColumns: Seq[String]): Task[AdminCriteria] =
    AdminCriteriaValidator.validateParams(pocAdmin.id, params, validSortColumns) match {
      case Validated.Valid(a)   => Task(a)
      case Validated.Invalid(e) => Task.raiseError(ValidationError(e))
    }
}
