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
import com.ubirch.db.tables.{ PocAdminRepository, PocEmployeeRepository }
import com.ubirch.models.NOK
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.poc.employee._
import com.ubirch.services.poc.{ CertifyUserService, Remove2faTokenError }
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra._
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }

import java.util.UUID
import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext
import scala.util._

@Singleton
class PocAdminController @Inject() (
  val swagger: Swagger,
  config: Config,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  pocAdminRepository: PocAdminRepository,
  csvProcessPocEmployee: CsvProcessPocEmployee,
  tokenVerificationService: TokenVerificationService,
  certifyUserService: CertifyUserService,
  pocEmployeeRepository: PocEmployeeRepository
)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {

  val createListOfEmployees: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("create list of employees")
      .summary("PoC employee batch creation")
      .description("Receives a semicolon separated .csv with a list of PoC employees." +
        " In case of not parsable rows, these will be returned in the answer with a specific remark.")
      .tags("PoC", "PoC-Admin", "PoC-Employee")
  val delete2FATokenOnPocEmployee: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Delete 2FA token")
      .summary("Deletes 2FA token for PoC employee")
      .description("Deletes 2FA token for PoC employee")
      .tags("Poc-Admin", "Poc-employee")
      .authorizations()
      .parameters(queryParam[UUID]("id").description("PoC admin id"))

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

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, CertifyKeycloak, tokenVerificationService, publicKeyPoolService)

  override protected def applicationDescription: String = "PoC Admin Controller"

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

  delete("/poc-employee/:id/2fa-token", operation(delete2FATokenOnPocEmployee)) {
    pocAdminEndpoint("Delete 2FA token for PoC admin") { _ =>
      getParamAsUUID("id", id => s"Invalid poc employee id: '$id'") { id =>
        for {
          r <- certifyUserService.remove2FAToken(id, pocEmployeeRepository.getPocEmployee)
            .map {
              case Left(e) => e match {
                  case Remove2faTokenError.CertifyUserNotFound(id) =>
                    NotFound(NOK.resourceNotFoundError(s"Poc employee with id '$id' not found'"))
                  case Remove2faTokenError.MissingCertifyUserId(id) =>
                    Conflict(NOK.conflict(s"Poc employee '$id' does not have certifyUserId"))
                }
              case Right(_) => Ok("")
            }
        } yield r
      }
    }
  }

  private def pocAdminEndpoint(description: String)(logic: Token => Task[ActionResult]) = {
    authenticated(_.hasRole(Token.POC_ADMIN)) { token: Token =>
      asyncResult(description) { _ => _ => logic(token) }
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
