package com.ubirch.controllers

import cats.data.Validated
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.EndpointHelpers._
import com.ubirch.controllers.SwitchActiveError.{
  MissingCertifyUserId,
  NotAllowedError,
  ResourceNotFound,
  UserNotCompleted
}
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Token,
  _
}
import com.ubirch.controllers.model.PocAdminControllerJsonModel._
import com.ubirch.controllers.validator.{ AdminCriteriaValidator, PocEmployeeInValidator }
import com.ubirch.db.tables.model.AdminCriteria
import com.ubirch.db.tables.{ PocAdminRepository, PocEmployeeRepository, TenantRepository }
import com.ubirch.models.poc.{ Completed, PocAdmin }
import com.ubirch.models.{ NOK, Paginated_OUT, ValidationError, ValidationErrorsResponse }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.keycloak.users.Remove2faTokenKeycloakError.UserNotFound
import com.ubirch.services.keycloak.users.{ Remove2faTokenKeycloakError, UpdateEmployeeKeycloakError }
import com.ubirch.services.poc.Remove2faTokenError.KeycloakError
import com.ubirch.services.poc.employee.{ EmptyCSVError, _ }
import com.ubirch.services.poc.{ CertifyUserService, Remove2faTokenError }
import com.ubirch.services.pocadmin.{
  GetEmployeeForPocAdminError,
  GetPocsAdminErrors,
  PocAdminService,
  UpdatePocEmployeeError
}
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.joda.time.DateTime
import org.json4s.Formats
import org.json4s.native.Serialization.read
import org.scalatra._
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID
import javax.inject.{ Inject, Singleton }
import scala.concurrent.ExecutionContext
import scala.util._
import com.ubirch.util.KeycloakRealmsHelper._

@Singleton
class PocAdminController @Inject() (
  val swagger: Swagger,
  config: Config,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  pocAdminRepository: PocAdminRepository,
  csvProcessPocEmployee: CsvProcessPocEmployee,
  tokenVerificationService: TokenVerificationService,
  pocAdminService: PocAdminService,
  certifyUserService: CertifyUserService,
  pocEmployeeRepository: PocEmployeeRepository,
  tenantRepository: TenantRepository,
  clock: Clock,
  x509CertSupport: X509CertSupport
)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, CertifyKeycloak, tokenVerificationService, publicKeyPoolService)

  override protected def applicationDescription: String = "PoC Admin Controller"

  implicit override protected def jsonFormats: Formats = jFormats

  override val service: String = config.getString(GenericConfPaths.NAME)

  override val successCounter: Counter = Counter
    .build()
    .name("poc_admin_success")
    .help("Represents the number of poc admin operation successes")
    .labelNames("service", "method")
    .register()

  override val errorCounter: Counter = Counter
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

  val getEmployee: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve an employee by id")
      .summary("Get Employee")
      .description("Retrieve Employee that belong to the querying PocAdmin.")
      .tags("PoC", "PoC Admin", "Poc-Employee")
      .parameters(queryParam[UUID]("Id of the Poc Employee"))
      .authorizations()

  val putEmployee: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Update an employee by id")
      .summary("Update Employee")
      .description("Update an Employee that belong to the querying PocAdmin.")
      .tags("PoC", "PoC Admin", "Poc-Employee")
      .parameters(queryParam[UUID]("Id of the Poc Employee"))
      .authorizations()

  val switchActiveOnPocEmployee: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Activate or deactivate PoC employee")
      .summary("De- and activation")
      .description("Activate or deactivate PoC admin")
      .tags("Poc-Employee", "Poc-Admin")
      .authorizations()
      .parameters(
        queryParam[UUID]("id").description("PoC employee id"),
        queryParam[Int]("isActive").description(
          "Whether PoC Employee should be active, values: 1 for true, 0 for false.")
      )

  val delete2FATokenOnPocEmployee: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Delete Employee 2FA token")
      .summary("Deletes 2FA token for PoC employee")
      .description("Deletes 2FA token for PoC employee")
      .tags("Poc-Admin", "Poc-employee")
      .authorizations()
      .parameters(queryParam[UUID]("id").description("PoC admin id"))

  post("/employees/create", operation(createListOfEmployees)) {
    x509CertSupport.withVerification(request) {
      pocAdminEndpoint("Create employees by CSV file") { pocAdmin =>
        readBodyWithCharset(request, StandardCharsets.UTF_8).flatMap { body =>
          csvProcessPocEmployee.createListOfPocEmployees(body, pocAdmin).map {
            case Left(UnknownTenant(tenantId)) =>
              logger.error(s"Could not find tenant with id $tenantId (assigned to ${pocAdmin.id} PocAdmin)")
              Ok("Could not find tenant assigned to given PocAdmin")
            case Left(HeaderParsingError(msg)) =>
              logger.error(s"Error has occurred during header parsing: $msg sent by ${pocAdmin.id}")
              Ok(s"Header in CSV file is not correct. $msg")
            case Left(EmptyCSVError(msg)) =>
              logger.error(s"Empty CSV file received from ${pocAdmin.id} PoC Admin: $msg")
              Ok("Empty CSV body")
            case Left(PocAdminNotInCompletedStatus(pocAdminId)) =>
              logger.error(s"Could not create employees because PocAdmin is not in completed state: $pocAdminId")
              InternalServerError(NOK.serverError("PoC Admin is not fully setup"))
            case Left(UnknownCsvParsingError(msg)) =>
              logger.error(s"Unexpected error has occurred while parsing the CSV: $msg")
              InternalServerError(NOK.serverError("Unknown error has happened while parsing the CSV file"))
            case Left(CsvContainedErrors(errors)) => Ok(errors)
            case Right(_)                         => Ok()
          }
        }.onErrorHandle { ex =>
          InternalServerError(NOK.serverError(
            s"something went wrong retrieving employees for admin with id ${pocAdmin.id}" + ex.getMessage))
        }
      }
    }
  }

  get("/employees", operation(getEmployees)) {
    x509CertSupport.withVerification(request) {
      pocAdminEndpoint("get employees") { pocAdmin =>
        (for {
          adminCriteria <- handleValidation(pocAdmin, AdminCriteriaValidator.validSortColumnsForEmployees)
          employees <- pocAdminService.getEmployees(pocAdmin, adminCriteria)
        } yield employees).map {
          case Right(employees) =>
            val paginated_OUT = Paginated_OUT(employees.total, employees.records.map(PocEmployee_OUT.fromPocEmployee))
            Presenter.toJsonResult(paginated_OUT)
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

  get("/employees/:id", operation(getEmployee)) {
    x509CertSupport.withVerification(request) {
      pocAdminEndpoint("Retrieve employee by id") { pocAdmin =>
        getParamAsUUID("id", id => s"Invalid PocEmployee id '$id'") { id =>
          pocAdminService.getEmployeeForPocAdmin(pocAdmin, id).map {
            case Right(pe) => Presenter.toJsonResult(PocEmployee_OUT.fromPocEmployee(pe))
            case Left(e) => e match {
                case GetEmployeeForPocAdminError.NotFound(id) =>
                  NotFound(NOK.resourceNotFoundError(s"Poc employee with id '$id' does not exist"))
                case GetEmployeeForPocAdminError.DoesNotBelongToTenant(_, _) =>
                  Unauthorized(NOK.authenticationError("Unauthorized"))
              }
          }
        }
      }
    }
  }

  put("/employees/:id", operation(putEmployee)) {
    x509CertSupport.withVerification(request) {
      pocAdminEndpoint("Update employee by id") { pocAdmin =>
        getParamAsUUID("id", id => s"Invalid PocEmployee id '$id'") { id =>
          for {
            body <- readBodyWithCharset(request, StandardCharsets.UTF_8)
            unvalidatedIn <- Task(read[PocEmployee_IN](body))
            validatedIn <- Task(PocEmployeeInValidator.validate(unvalidatedIn))
            r <- validatedIn match {
              case Validated.Invalid(e) =>
                Presenter.toJsonStr(ValidationErrorsResponse(e.toNonEmptyList.toList.toMap))
                  .map(BadRequest(_))
              case Validated.Valid(pocEmployeeIn) =>
                pocAdminService.updateEmployee(pocAdmin, id, pocEmployeeIn).map {
                  case Right(_) => Ok()
                  case Left(e) => e match {
                      case UpdatePocEmployeeError.NotFound(id) =>
                        NotFound(NOK.resourceNotFoundError(s"Poc employee with id '$id' does not exist"))
                      case UpdatePocEmployeeError.DoesNotBelongToTenant(_, _) =>
                        Unauthorized(NOK.authenticationError("Unauthorized"))
                      case UpdatePocEmployeeError.WrongStatus(id, status, expectedStatus) =>
                        Conflict(
                          NOK.conflict(
                            s"Poc employee '$id' is in wrong status: '$status', required: '$expectedStatus'"))
                      case UpdatePocEmployeeError.KeycloakError(e) => e match {
                          case UpdateEmployeeKeycloakError.UserNotFound(_) =>
                            InternalServerError(
                              NOK.serverError(s"Poc employee '$id' is assigned to not existing certify user"))
                          case UpdateEmployeeKeycloakError.KeycloakError(error) =>
                            InternalServerError(NOK.serverError(s"Certify user services responded with error: $error"))
                          case UpdateEmployeeKeycloakError.MissingCertifyUserId(_) =>
                            InternalServerError(NOK.serverError(s"Poc employee '$id' is not assigned to certify user"))
                        }
                    }
                }
            }
          } yield r
        }
      }
    }
  }

  put("/employees/:id/active/:isActive", operation(switchActiveOnPocEmployee)) {
    x509CertSupport.withVerification(request) {
      pocAdminEndpoint("Switch active flag for PoC Employee") { pocAdmin =>
        getParamAsUUID("id", id => s"Invalid PocEmployee id '$id'") { employeeId =>
          (for {
            switch <- Task(ActivateSwitch.fromIntUnsafe(params("isActive").toInt))
            r <- pocAdminService.switchActiveForPocEmployee(employeeId, pocAdmin, switch)
              .map {
                case Left(e) => e match {
                    case ResourceNotFound(id) =>
                      NotFound(
                        NOK.resourceNotFoundError(s"Poc employee with id '$id' or related tenant was not found'"))
                    case UserNotCompleted => Conflict(NOK.conflict(
                        s"Poc employee with id '$employeeId' cannot be de/-activated before status is Completed."))
                    case MissingCertifyUserId(id) =>
                      Conflict(NOK.conflict(s"Poc employee '$id' does not have certifyUserId yet"))
                    case NotAllowedError =>
                      Unauthorized(NOK.authenticationError(
                        s"Poc employee with id '$employeeId' doesn't belong to poc of requesting poc admin."))
                  }
                case Right(_) => Ok("")
              }.onErrorHandle { ex =>
                logger.error("something unexpected happened during de-/ activating the poc employee", ex)
                InternalServerError(NOK.serverError("unexpected error"))
              }
          } yield r).onErrorRecover {
            case e: IllegalValueForActivateSwitch => BadRequest(NOK.badRequest(e.getMessage))
          }
        }
      }
    }
  }

  delete("/employees/:id/2fa-token", operation(delete2FATokenOnPocEmployee)) {
    x509CertSupport.withVerification(request) {
      pocAdminEndpoint("Delete 2FA token for PoC admin") { pocAdmin =>
        getParamAsUUID("id", id => s"Invalid poc employee id: '$id'") { id =>
          for {
            maybePocEmployee <- pocEmployeeRepository.getPocEmployee(id)
            maybeTenant <- tenantRepository.getTenant(pocAdmin.tenantId)
            notFoundMessage = s"Poc employee with id '$id' not found'"
            r <- maybePocEmployee match {
              case None => Task.pure(NotFound(NOK.resourceNotFoundError(notFoundMessage)))
              case Some(certifyUser) if certifyUser.pocId != pocAdmin.pocId =>
                Task.pure(Unauthorized(NOK.authenticationError(notFoundMessage)))
              case Some(certifyUser) if certifyUser.status != Completed =>
                Task.pure(Conflict(NOK.conflict(
                  s"Poc employee '$id' is in wrong status: '${certifyUser.status}', required: '$Completed'")))
              case Some(_) if maybeTenant.isEmpty =>
                Task.pure(NotFound(NOK.resourceNotFoundError(s"Related tenant was not found for poc employee $id")))
              case Some(certifyUser) =>
                certifyUserService.remove2FAToken(maybeTenant.get.getRealm, certifyUser)
                  .flatMap {
                    case Left(e) => e match {
                        case KeycloakError(_, keyCloakError) =>
                          keyCloakError match {
                            case UserNotFound(error) =>
                              Task.pure(NotFound(NOK.resourceNotFoundError(error)))
                            case Remove2faTokenKeycloakError.KeycloakError(error) =>
                              Task.pure(InternalServerError(NOK.serverError(error)))
                          }
                        case Remove2faTokenError.MissingCertifyUserId(id) =>
                          Task.pure(Conflict(NOK.conflict(s"Poc employee '$id' does not have certifyUserId")))
                      }
                    case Right(_) =>
                      pocEmployeeRepository.updatePocEmployee(certifyUser.copy(webAuthnDisconnected =
                        Some(DateTime.parse(clock.instant().toString)))) >>
                        Task.pure(Ok(""))
                  }
            }
          } yield r
        }
      }
    }
  }

  private def pocAdminEndpoint(description: String)(logic: PocAdmin => Task[ActionResult]) = {
    authenticated(_.hasRole(Token.POC_ADMIN)) { token: Token =>
      asyncResult(description) { _ => _ =>
        retrievePocAdminFromToken(token, pocAdminRepository) { pocAdmin =>
          logic(pocAdmin)
        }
      }
    }
  }

  private def handleValidation(pocAdmin: PocAdmin, validSortColumns: Seq[String]): Task[AdminCriteria] =
    AdminCriteriaValidator.validateParams(pocAdmin.id, params, validSortColumns) match {
      case Validated.Valid(a)   => Task(a)
      case Validated.Invalid(e) => Task.raiseError(ValidationError(e))
    }

  private def getParamAsUUID(
    paramName: String,
    errorMsg: String => String)(logic: UUID => Task[ActionResult]): Task[ActionResult] = {
    val id = params(paramName)
    Try(UUID.fromString(id)) match {
      case Success(uuid) => logic(uuid)
      case Failure(ex) =>
        val error = errorMsg(id)
        logger.error(error, ex)
        Task(BadRequest(NOK.badRequest(error)))
    }
  }
}
