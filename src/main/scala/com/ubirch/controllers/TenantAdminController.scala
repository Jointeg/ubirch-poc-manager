package com.ubirch.controllers

import cats.data.Validated
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.EndpointHelpers.{ retrieveTenantFromToken, ActivateSwitch, IllegalValueForActivateSwitch }
import com.ubirch.controllers.SwitchActiveError.{
  MissingCertifyUserId,
  NotAllowedError,
  ResourceNotFound,
  UserNotCompleted
}
import com.ubirch.controllers.concerns._
import com.ubirch.controllers.validator.{ CriteriaValidator, PocAdminInValidator }
import com.ubirch.db.tables.{ PocAdminRepository, PocRepository, PocStatusRepository, TenantTable }
import com.ubirch.models.poc._
import com.ubirch.models.tenant._
import com.ubirch.models.{ NOK, Paginated_OUT, ValidationError, ValidationErrorsResponse }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.keycloak.users.Remove2faTokenKeycloakError
import com.ubirch.services.poc.{ CertifyUserService, PocBatchHandlerImpl, Remove2faTokenError }
import com.ubirch.services.tenantadmin.GetPocAdminStatusErrors._
import com.ubirch.services.tenantadmin._
import com.ubirch.services.util.Validator
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.joda.time.DateTime
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{ read, write }
import org.scalatra._
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util._

class TenantAdminController @Inject() (
  pocBatchHandler: PocBatchHandlerImpl,
  pocStatusTable: PocStatusRepository,
  pocTable: PocRepository,
  pocAdminRepository: PocAdminRepository,
  tenantTable: TenantTable,
  config: Config,
  val swagger: Swagger,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  tokenVerificationService: TokenVerificationService,
  tenantAdminService: TenantAdminService,
  certifyUserService: CertifyUserService,
  clock: Clock)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {

  import com.ubirch.controllers.model.TenantAdminControllerJsonModel._

  implicit override protected def jsonFormats: Formats = jFormats

  override protected def applicationDescription: String = "Tenant Admin Controller"

  override val service: String = config.getString(GenericConfPaths.NAME)

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, CertifyKeycloak, tokenVerificationService, publicKeyPoolService)

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
    apiOperation[Paginated_OUT[Poc]]("create list of PoCs")
      .summary("PoC batch creation")
      .description("Receives a semicolon separated .csv with a list of PoC details to create the PoCs." +
        " In case of not parsable rows, these will be returned in the answer with a specific remark.")
      .tags("PoC", "Tenant-Admin")

  val deviceToken: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[Paginated_OUT[Poc]]("update device creation token for tenant")
      .summary("updated device creation token")
      .description("receives a CREATE-THING and GET-INFO token and store it encrypted to the tenant object")
      .tags("Tenant", "device", "token")

  val getPocStatus: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[PocStatus]("retrieve PoC Status via pocId")
      .summary("Get PoC Status")
      .description("Retrieve PoC Status queried by pocId. If it doesn't exist 404 is returned.")
      .tags("Tenant-Admin", "PocStatus")

  val getPocs: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve all pocs of the requesting tenant")
      .summary("Get PoCs")
      .description("Retrieve PoCs that belong to the querying tenant.")
      .tags("Tenant-Admin", "PoCs")
      .authorizations()
  val getPoc: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve poc by id")
      .summary("Get PoC")
      .description("Retrieve PoC that belong to the querying tenant.")
      .tags("Tenant-Admin", "PoC")
      .authorizations()
  val putPoc: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Update poc by id")
      .summary("Update PoC")
      .description("Update PoC that belong to the querying tenant.")
      .tags("Tenant-Admin", "PoC")
      .authorizations()

  val getPocAdmins: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve all poc admins of the requesting tenant")
      .summary("Get PoC admins")
      .description("Retrieve PoC admins that belong to the querying tenant.")
      .tags("Tenant-Admin", "PoCs", "PoC Admins")
      .authorizations()
  val getPocAdmin: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve poc admin of the requesting tenant")
      .summary("Get PoC admin")
      .description("Retrieve PoC admin that belong to the querying tenant.")
      .tags("Tenant-Admin", "PoCs", "PoC Admin")
      .authorizations()
  val putPocAdmin: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("update poc admin of the requesting tenant")
      .summary("update PoC admin")
      .description("Update PoC admin that belong to the querying tenant.")
      .tags("Tenant-Admin", "PoCs", "PoC Admin")
      .authorizations()
  val getPocAdminStatus: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[PocAdminStatus]("retrieve PoC Admin Status via pocAdminId")
      .summary("Get PoC Admin Status")
      .description("Retrieve PoC Admin Status queried by pocAdminId")
      .tags("Tenant-Admin", "PocAdminStatus")

  val getDevices: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Retrieve all devices from all PoCs of Tenant")
      .summary("Get devices")
      .description("Retrieves all devices that belongs to PoCs that are managed by querying Tenant.")
      .tags("Tenant-Admin", "Devices")
      .authorizations()

  val updateWebIdentId: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("UpdateWebIdentId")
      .summary("Updates WebIdent Identifier for given PoC Admin")
      .tags("Tenant-Admin")
      .authorizations()
      .parameters(
        bodyParam[String]("pocAdminId").description("ID of PoC admin for which identifier will be assigned"),
        bodyParam[String]("webIdentId").description("WebIdent identifier"),
        bodyParam[String]("webIdentInitialId").description("WebIdent initial ID")
      )

  val createWebInitiateId: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Create WebInitiateId for given PoC admin")
      .summary("Create WebInitiateId")
      .description("Creates WebInitiateId for given PoC admin")
      .tags("Tenant-Admin", "WebIdent")
      .authorizations()
      .parameters(
        bodyParam[String]("pocAdminId").description("ID of PocAdmin for which WebInitiateId will be created")
      )

  val switchActiveOnPocAdmin: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Activate or deactivate PoC admin")
      .summary("Activate or deactivate PoC admin")
      .description("Activate or deactivate PoC admin")
      .tags("Tenant-Admin", "Poc-Admin")
      .authorizations()
      .parameters(
        queryParam[UUID]("id").description("PoC admin id"),
        queryParam[Int]("isActive").description("Whether PoC Admin should be active, values: 1 for true, 0 for false.")
      )

  val delete2FATokenOnPocAdmin: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("Delete 2FA token")
      .summary("Deletes 2FA token for PoC admin")
      .description("Deletes 2FA token for PoC admin")
      .tags("Tenant-Admin", "Poc-Admin")
      .authorizations()
      .parameters(queryParam[UUID]("id").description("PoC admin id"))

  post("/pocs/create", operation(createListOfPocs)) {
    tenantAdminEndpointWithUserContext("Create poc batch") { (tenant, tenantContext) =>
      readBodyWithCharset(request, StandardCharsets.UTF_8).flatMap { body =>
        pocBatchHandler
          .createListOfPoCs(body, tenant, tenantContext)
          .map {
            case Right(_)  => Ok()
            case Left(csv) => Ok(csv)
          }
      }.onErrorHandle { ex =>
        InternalServerError(NOK.serverError(
          s"something went wrong retrieving pocs for tenant with id ${tenant.id}" + ex.getMessage))
      }
    }
  }

  get("/poc/:id", operation(getPoc)) {
    tenantAdminEndpoint("Get PoC for a tenant") { tenant =>
      getParamAsUUID("id", id => s"Invalid poc id '$id'") { id =>
        tenantAdminService.getPocForTenant(tenant, id).map {
          case Left(e) => e match {
              case GetPocForTenantError.NotFound(pocId) =>
                NotFound(NOK.resourceNotFoundError(s"PoC with id '$pocId' does not exist"))
              case GetPocForTenantError.AssignedToDifferentTenant(pocId, tenantId) =>
                Unauthorized(NOK.authenticationError(
                  s"PoC with id '$pocId' does not belong to tenant with id '${tenantId.value.value}'"))
            }
          case Right(p) => Presenter.toJsonResult(p)
        }
      }
    }
  }

  put("/poc/:id", operation(putPoc)) {
    tenantAdminEndpoint("Update PoC for a tenant") { tenant =>
      getParamAsUUID("id", id => s"Invalid poc id '$id'") { id =>
        for {
          body <- readBodyWithCharset(request, StandardCharsets.UTF_8)
          r <- tenantAdminService.updatePoc(tenant, id, Serialization.read[Poc_IN](body)).map {
            case Left(e) => e match {
                case UpdatePocError.NotFound(pocId) =>
                  NotFound(NOK.resourceNotFoundError(s"PoC with id '$pocId' does not exist"))
                case UpdatePocError.AssignedToDifferentTenant(pocId, tenantId) =>
                  Unauthorized(NOK.authenticationError(
                    s"PoC with id '$pocId' does not belong to tenant with id '${tenantId.value.value}'"))
                case UpdatePocError.NotCompleted(pocId, status) =>
                  Conflict(NOK.conflict(s"Poc '$pocId' is in wrong status: '$status', required: '$Completed'"))
                case UpdatePocError.ValidationError(message) =>
                  BadRequest(NOK.badRequest(message))
              }
            case Right(p) => Presenter.toJsonResult(p)
          }
        } yield r
      }
    }
  }

  get("/pocs", operation(getPocs)) {
    tenantAdminEndpoint("Get all PoCs for a tenant") { tenant =>
      (for {
        criteria <- handleValidation(tenant, CriteriaValidator.validSortColumnsForPoc)
        pocs <- pocTable.getAllPocsByCriteria(criteria)
      } yield Paginated_OUT(pocs.total, pocs.records))
        .map(Presenter.toJsonResult)
        .onErrorRecoverWith {
          case ValidationError(e) =>
            Presenter.toJsonStr(ValidationErrorsResponse(e.toNonEmptyList.toList.toMap))
              .map(BadRequest(_))
        }
        .onErrorHandle { ex =>
          InternalServerError(NOK.serverError(
            s"something went wrong retrieving pocs for tenant with id ${tenant.id}" + ex.getMessage))
        }
    }
  }

  post("/deviceToken", operation(deviceToken)) {
    tenantAdminEndpointWithUserContext("Add CREATE DEVICE and GET INFO token to tenant") {
      (tenant, tenantAdminContext) =>
        tenantAdminService
          .addDeviceCreationToken(
            tenant,
            tenantAdminContext,
            Serialization.read[AddDeviceCreationTokenRequest](request.body)
          )
          .map {
            case Right(_) => Ok()
            case Left(errorMessage: String) =>
              logger.error(s"failed to add deviceCreationToken $errorMessage")
              InternalServerError("failed to device creation token update")
          }.onErrorHandle { ex =>
            logger.error(s"failed to add deviceCreationToken", ex)
            InternalServerError("something went wrong on device creation token update")
          }
    }
  }

  get("/pocStatus/:id", operation(getPocStatus)) {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { _ =>
      asyncResult("Get Poc Status") { _ => _ =>
        getParamAsUUID("id", id => s"error on retrieving pocStatus with $id:") { uuid =>
          pocStatusTable
            .getPocStatus(uuid)
            .map {
              case Some(pocStatus) => Presenter.toJsonResult(pocStatus)
              case None            => NotFound(NOK.resourceNotFoundError(s"pocStatus with $uuid couldn't be found"))
            }
        }
      }
    }
  }

  get("/devices", operation(getDevices)) {
    tenantAdminEndpoint("Get all Devices for all PoCs of tenant") { tenant =>
      tenantAdminService.getSimplifiedDeviceInfoAsCSV(tenant).map(devicesInformation => {
        response.setContentType("text/csv")
        response.setHeader("Content-Disposition", "attachment; filename=simplified-devices-info.csv")
        Ok(devicesInformation)
      })
    }
  }

  post("/webident/initiate-id", operation(createWebInitiateId)) {
    import CreateWebIdentInitiateIdErrors._
    tenantAdminEndpointWithUserContext("Create web initiate id") { (tenant, tenantAdminContext) =>
      tenantAdminService.createWebIdentInitiateId(
        tenant,
        tenantAdminContext,
        Serialization.read[CreateWebIdentInitiateIdRequest](request.body)).map {
        case Right(webInitiateId) => Ok(Presenter.toJsonResult(CreateWebInitiateIdResponse(webInitiateId)))
        case Left(PocAdminNotFound(pocAdminId)) =>
          logger.error(s"Could not find PocAdmin with id: $pocAdminId")
          NotFound(NOK.resourceNotFoundError("Could not find PoC admin with provided ID"))
        case Left(PocAdminAssignedToDifferentTenant(tenantId, pocAdminTenantId)) =>
          logger.error(s"Tenant with ID $tenantId tried to operate on PoC Admin with ID $pocAdminTenantId who is assigned to different tenant")
          NotFound(NOK.resourceNotFoundError("Could not find PoC admin with provided ID"))
        case Left(WebIdentNotRequired(tenantId, pocAdminId)) =>
          logger.error(s"Tenant with ID $tenantId tried to create WebInitiateId but PoC admin with ID $pocAdminId does not require WebIdent")
          BadRequest(NOK.badRequest("PoC admin does not require WebIdent"))
        case Left(PocAdminRepositoryError(msg)) =>
          logger.error(s"Error has occurred while operating on PocAdmin table: $msg")
          InternalServerError(NOK.serverError("Could not create PoC admin WebInitiateId"))
        case Left(WebIdentAlreadyInitiated(webIdentInitiateId)) =>
          Try(write[CreateWebInitiateIdResponse](CreateWebInitiateIdResponse(webIdentInitiateId))) match {
            case Success(json) => Conflict(json)
            case Failure(ex) =>
              val errorMsg = s"Could not parse ${CreateWebInitiateIdResponse.getClass.getSimpleName} to json"
              logger.error(errorMsg, ex)
              InternalServerError(NOK.serverError(errorMsg))
          }
      }
    }
  }

  post("/webident/id", operation(updateWebIdentId)) {
    tenantAdminEndpointWithUserContext("Update Webident identifier") { (tenant, tenantAdminContext) =>
      import UpdateWebIdentIdError._
      tenantAdminService.updateWebIdentId(
        tenant,
        tenantAdminContext,
        Serialization.read[UpdateWebIdentIdRequest](request.body)).map {
        case Right(_) => Ok("")
        case Left(UnknownPocAdmin(id)) =>
          logger.error(s"Could not find PoC Admin with id $id")
          NotFound(NOK.resourceNotFoundError("Could not find PoC Admin with provided ID"))
        case Left(WebIdentAlreadyExist(pocAdminId)) =>
          logger.error(s"WebIdent Id already exist $pocAdminId")
          BadRequest(NOK.badRequest("WebIdent Id already exists"))
        case Left(PocAdminIsNotAssignedToRequestingTenant(pocAdminTenantId, requestingTenantId)) =>
          logger.error(
            s"Requesting tenant with ID $requestingTenantId asked to change WebIdent for admin with id $pocAdminTenantId that is not under his assignment")
          NotFound(NOK.resourceNotFoundError("Could not find PoC Admin with provided ID"))
        case Left(DifferentWebIdentInitialId(requestWebIdentInitialId, tenant, pocAdmin)) =>
          logger.error(
            s"Requesting Tenant (${tenant.id}) tried to update WebIdent ID of PoC admin (${pocAdmin.id}) but sent WebIdentInitialID ($requestWebIdentInitialId) does not match the one that is assigned to PoC Admin")
          BadRequest(NOK.badRequest("Wrong WebIdentInitialId"))
        case Left(NotExistingPocAdminStatus(id)) =>
          logger.error(s"Could not find PoC Admin status for id $id")
          NotFound(NOK.resourceNotFoundError("Could not find Poc Admin Status assigned to given PoC Admin"))
      }
    }
  }

  get("/poc-admins", operation(getPocAdmins)) {
    tenantAdminEndpoint("Get all PoC Admins for a tenant") { tenant =>
      (for {
        criteria <- handleValidation(tenant, CriteriaValidator.validSortColumnsForPocAdmin)
        pocAdmins <- pocAdminRepository.getAllByCriteria(criteria)
      } yield Paginated_OUT(
        pocAdmins.total,
        pocAdmins.records.map { case (pa, p) => PocAdmin_OUT.fromPocAdmin(pa, p) }))
        .map(Presenter.toJsonResult)
        .onErrorRecoverWith {
          case ValidationError(e) =>
            Presenter.toJsonStr(ValidationErrorsResponse(e.toNonEmptyList.toList.toMap))
              .map(BadRequest(_))
        }
        .onErrorHandle { ex =>
          InternalServerError(NOK.serverError(
            s"something went wrong retrieving pocs for tenant with id ${tenant.id}" + ex.getMessage))
        }
    }
  }

  get("/poc-admin/status/:id", operation(getPocAdminStatus)) {
    tenantAdminEndpoint("Get status of PoC Admin") { tenant =>
      getParamAsUUID("id", id => s"Could not convert provided ID ($id) to UUID") { pocAdminId =>
        tenantAdminService.getPocAdminStatus(tenant, pocAdminId).map {
          case Right(getPocAdminStatusResponse) =>
            Presenter.toJsonResult(getPocAdminStatusResponse)
          case Left(PocAdminNotFound(pocAdminId)) =>
            logger.error(s"Could not find PoC Admin with id $pocAdminId")
            NotFound(NOK.resourceNotFoundError("Could not find PoC Admin"))
          case Left(PocAdminAssignedToDifferentTenant(tenantId, pocAdminId)) =>
            logger.error(
              s"Tenant $tenantId tried to get status of PoC Admin $pocAdminId that is not under his assignment")
            NotFound(NOK.resourceNotFoundError("Could not find PoC Admin status with provided ID"))
          case Left(PocAdminStatusNotFound(pocAdminId)) =>
            logger.error(s"Could not find PoC Admin Status for PoC Admin with id $pocAdminId")
            NotFound(NOK.resourceNotFoundError("Could not find Poc Admin Status assigned to provided PoC Admin"))
        }
      }
    }
  }

  put("/poc-admin/:id/active/:isActive", operation(switchActiveOnPocAdmin)) {
    tenantAdminEndpointWithUserContext("Switch active flag for PoC Admin") { (_, tenantContext) =>
      getParamAsUUID("id", id => s"Invalid PocAdmin id '$id'") { adminId =>
        (for {
          switch <- Task(ActivateSwitch.fromIntUnsafe(params("isActive").toInt))
          r <- tenantAdminService.switchActiveForPocAdmin(adminId, tenantContext, switch)
            .map {
              case Left(e) => e match {
                  case ResourceNotFound(id) =>
                    NotFound(NOK.resourceNotFoundError(s"Poc admin with id '$id' not found'"))
                  case UserNotCompleted =>
                    Conflict(NOK.conflict(
                      s"Poc admin with id '$adminId' cannot be de/-activated before status is Completed."))
                  case MissingCertifyUserId(id) =>
                    Conflict(NOK.conflict(s"Poc admin '$id' does not have certifyUserId"))
                  case NotAllowedError =>
                    Unauthorized(NOK.authenticationError(
                      s"Poc admin with id '$adminId' doesn't belong to requesting tenant admin."))
                }
              case Right(_) => Ok("")
            }.onErrorHandle { ex =>
              logger.error("something unexpected happened during de-/ activating the poc admin", ex)
              InternalServerError(NOK.serverError("unexpected error"))
            }
        } yield r).onErrorRecover {
          case e: IllegalValueForActivateSwitch => BadRequest(NOK.badRequest(e.getMessage))
        }
      }
    }
  }

  delete("/poc-admin/:id/2fa-token", operation(delete2FATokenOnPocAdmin)) {
    if (true) NotFound(NOK.noRouteFound("Sorry, this method is not yet fully implemented."))
    else {
      tenantAdminEndpoint("Delete 2FA token for PoC admin") { tenant =>
        getParamAsUUID("id", id => s"Invalid PocAdmin id '$id'") { pocAdminId =>
          for {
            maybePocAdmin <- pocAdminRepository.getPocAdmin(pocAdminId)
            notFoundMessage = s"Poc admin with id '$pocAdminId' not found'"
            r <- maybePocAdmin match {
              case None => Task.pure(NotFound(NOK.resourceNotFoundError(notFoundMessage)))
              case Some(pocAdmin) if pocAdmin.tenantId != tenant.id =>
                Task.pure(NotFound(NOK.resourceNotFoundError(notFoundMessage)))
              case Some(pocAdmin) if pocAdmin.status != Completed =>
                Task.pure(Conflict(NOK.conflict(
                  s"Poc admin '$pocAdminId' is in wrong status: '${pocAdmin.status}', required: '${Completed}'")))
              case Some(pocAdmin) => certifyUserService.remove2FAToken(CertifyKeycloak.defaultRealm, pocAdmin)
                  .flatMap {
                    case Left(e) => e match {
                        case Remove2faTokenError.KeycloakError(_, error) =>
                          error match {
                            case Remove2faTokenKeycloakError.UserNotFound(error) =>
                              Task.pure(NotFound(NOK.resourceNotFoundError(error)))
                            case Remove2faTokenKeycloakError.KeycloakError(error) =>
                              Task.pure(InternalServerError(NOK.serverError(error)))
                          }
                        case Remove2faTokenError.MissingCertifyUserId(id) =>
                          Task.pure(Conflict(NOK.conflict(s"Poc admin '$id' does not have certifyUserId")))
                      }
                    case Right(_) =>
                      pocAdminRepository.updatePocAdmin(pocAdmin.copy(webAuthnDisconnected =
                        Some(DateTime.parse(clock.instant().toString)))) >>
                        Task.pure(Ok(""))
                  }
            }
          } yield r
        }
      }
    }
  }

  get("/poc-admin/:id", operation(getPocAdmin)) {
    tenantAdminEndpoint("Get PoC Admin for a tenant") { tenant =>
      getParamAsUUID("id", id => s"Invalid poc-admin id '$id'") { id =>
        tenantAdminService.getPocAdminForTenant(tenant, id)
          .map(pocAdminWithPoc => pocAdminWithPoc.map { case (pa, p) => PocAdmin_OUT.fromPocAdmin(pa, p) })
          .map {
            case Left(e) => e match {
                case GetPocAdminForTenantError.NotFound(pocAdminId) =>
                  NotFound(NOK.resourceNotFoundError(s"PoC Admin with id '$pocAdminId' does not exist"))
                case GetPocAdminForTenantError.AssignedToDifferentTenant(pocAdminId, tenantId) =>
                  Unauthorized(NOK.authenticationError(
                    s"PoC Admin with id '$pocAdminId' does not belong to tenant with id '${tenantId.value.value}'"))
              }
            case Right(p) => Presenter.toJsonResult(p)
          }
      }
    }
  }

  put("/poc-admin/:id", operation(putPocAdmin)) {
    tenantAdminEndpoint("Update PoC Admin for a tenant") { tenant =>
      getParamAsUUID("id", id => s"Invalid poc-admin id '$id'") { id =>
        for {
          body <- readBodyWithCharset(request, StandardCharsets.UTF_8)
          unvalidatedIn <- Task(read[PocAdmin_IN](body))
          validatedIn <- Task(PocAdminInValidator.validate(unvalidatedIn))
          r <- validatedIn match {
            case Validated.Invalid(e) =>
              Presenter.toJsonStr(ValidationErrorsResponse(e.toNonEmptyList.toList.toMap))
                .map(BadRequest(_))
            case Validated.Valid(pocAdminIn) =>
              tenantAdminService.updatePocAdmin(tenant, id, pocAdminIn).map {
                case Left(e) => e match {
                    case UpdatePocAdminError.NotFound(pocAdminId) =>
                      NotFound(NOK.resourceNotFoundError(s"PoC admin with id '$pocAdminId' does not exist"))
                    case UpdatePocAdminError.AssignedToDifferentTenant(_, _) =>
                      Unauthorized(NOK.authenticationError(
                        s"PoC admin with id '$id' does not belong to tenant with id '${tenant.id.value.value}'"))
                    case UpdatePocAdminError.InvalidStatus(pocAdminId, status) =>
                      Conflict(
                        NOK.conflict(s"Poc admin '$pocAdminId' is in wrong status: '$status', required: '$Completed'"))
                    case UpdatePocAdminError.WebIdentRequired =>
                      Conflict(NOK.conflict(s"Poc admin '$id' has webIdentRequired set to false"))
                    case UpdatePocAdminError.WebIdentInitiateIdAlreadySet =>
                      Conflict(NOK.conflict(s"Poc admin '$id' webIdentInitiateId is set"))
                  }
                case Right(_) => Ok()
              }
          }
        } yield r
      }
    }
  }

  private def tenantAdminEndpoint(description: String)(logic: Tenant => Task[ActionResult]) = {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token: Token =>
      asyncResult(description) { _ => _ =>
        retrieveTenantFromToken(token)(tenantTable).flatMap {
          case Right(tenant: Tenant) =>
            logic(tenant)
          case Left(errorMsg: String) =>
            logger.error(errorMsg)
            Task(BadRequest(NOK.authenticationError(errorMsg)))
        }
      }
    }
  }

  private def tenantAdminEndpointWithUserContext(description: String)(logic: (
    Tenant,
    TenantAdminContext) => Task[ActionResult]) = {

    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token: Token =>
      asyncResult(description) { _ => _ =>
        retrieveTenantFromToken(token)(tenantTable)
          .map((token.ownerIdAsUUID, _)).flatMap {
            case (Success(userId), Right(tenant: Tenant)) =>
              logic(tenant, TenantAdminContext(userId, tenant.id.value.asJava()))
            case (_, Left(errorMsg: String)) =>
              logger.error(errorMsg)
              Task(BadRequest(NOK.authenticationError(errorMsg)))
            case (Failure(uuid), Right(_)) =>
              Task(BadRequest(NOK.badRequest(s"Owner ID $uuid in token is not in UUID format")))
          }
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

  private def handleValidation(tenant: Tenant, validSortColumns: Seq[String]) =
    CriteriaValidator.validateParams(tenant.id, params, validSortColumns) match {
      case Validated.Valid(a)   => Task(a)
      case Validated.Invalid(e) => Task.raiseError(ValidationError(e))
    }
}

case class AddDeviceCreationTokenRequest(token: String)
