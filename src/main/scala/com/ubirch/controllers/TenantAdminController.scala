package com.ubirch.controllers

import cats.data.{ NonEmptyChain, Validated }
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.EndpointHelpers.retrieveTenantFromToken
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Token
}
import com.ubirch.controllers.validator.CriteriaValidator
import com.ubirch.db.tables.{ PocAdminRepository, PocRepository, PocStatusRepository, TenantTable }
import com.ubirch.models.poc._
import com.ubirch.models.tenant._
import com.ubirch.models.{ NOK, Response, ValidationErrorsResponse }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.poc.PocBatchHandlerImpl
import com.ubirch.services.tenantadmin.TenantAdminService.{ ActivateSwitch, IllegalValueForActivateSwitch }
import com.ubirch.services.tenantadmin._
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.joda.time.LocalDate
import org.json4s.Formats
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write
import org.scalatra._
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }

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
  tenantAdminService: TenantAdminService)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {

  import TenantAdminController._

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
  val getPocAdmins: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve all poc admins of the requesting tenant")
      .summary("Get PoC admins")
      .description("Retrieve PoC admins that belong to the querying tenant.")
      .tags("Tenant-Admin", "PoCs", "PoC Admins")
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

  post("/pocs/create", operation(createListOfPocs)) {
    tenantAdminEndpointWithUserContext("Create poc batch") { (tenant, tenantContext) =>
      pocBatchHandler
        .createListOfPoCs(request.body, tenant, tenantContext)
        .map {
          case Right(_)  => Ok()
          case Left(csv) => Ok(csv)
        }
    }
  }

  get("/pocs", operation(getPocs)) {
    tenantAdminEndpoint("Get all PoCs for a tenant") { tenant =>
      (for {
        criteria <- handleValidation(tenant, CriteriaValidator.validSortColumnsForPoc)
        pocs <- pocTable.getAllPocsByCriteria(criteria)
      } yield Paginated_OUT(pocs.total, pocs.records))
        .map(toJson)
        .onErrorRecoverWith {
          case ValidationError(e) =>
            ValidationErrorsResponse(e.toNonEmptyList.toList.toMap)
              .toJson
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
              case Some(pocStatus) => toJson(pocStatus)
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
        case Right(webInitiateId) => Ok(toJson(CreateWebInitiateIdResponse(webInitiateId)))
        case Left(PocAdminNotFound(pocAdminId)) =>
          logger.error(s"Could not find PocAdmin with id: $pocAdminId")
          NotFound(NOK.resourceNotFoundError("Could not find PoC admin with provided ID"))
        case Left(PocAdminAssignedToDifferentTenant(tenantId, pocAdminTenantId)) =>
          logger.error(s"Tenant with ID $tenantId tried to operate on PoC Admin with ID $pocAdminTenantId who is assigned to different tenant")
          NotFound(NOK.resourceNotFoundError("Could not find PoC admin with provided ID"))
        case Left(WebIdentNotRequired(tenantId, pocAdminId)) =>
          logger.error(s"Tenant with ID $tenantId tried to create WebInitiateId but PoC admin with ID $pocAdminId does not require WebIdent")
          BadRequest("PoC admin does not require WebIdent")
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
        case Left(PocAdminIsNotAssignedToRequestingTenant(pocAdminTenantId, requestingTenantId)) =>
          logger.error(
            s"Requesting tenant with ID $requestingTenantId asked to change WebIdent for admin with id $pocAdminTenantId that is not under his assignment")
          NotFound(NOK.resourceNotFoundError("Could not find PoC Admin with provided ID"))
        case Left(DifferentWebIdentInitialId(requestWebIdentInitialId, tenant, pocAdmin)) =>
          logger.error(
            s"Requesting Tenant (${tenant.id}) tried to update WebIdent ID of PoC admin (${pocAdmin.id}) but sent WebIdentInitialID ($requestWebIdentInitialId) does not match the one that is assigned to PoC Admin")
          BadRequest("Wrong WebIdentInitialId")
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
        .map(toJson)
        .onErrorRecoverWith {
          case ValidationError(e) =>
            ValidationErrorsResponse(e.toNonEmptyList.toList.toMap)
              .toJson
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
        import GetPocAdminStatusErrors._
        tenantAdminService.getPocAdminStatus(tenant, pocAdminId).map {
          case Right(getPocAdminStatusResponse) =>
            toJson(getPocAdminStatusResponse)
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
      getParamAsUUID("id", id => s"Invalid PocAdmin id '$id'") { pocAdminId =>
        (for {
          switch <- Task(ActivateSwitch.fromIntUnsafe(params("isActive").toInt))
          r <- tenantAdminService.switchActiveForPocAdmin(pocAdminId, tenantContext, switch)
            .map {
              case Left(e) => e match {
                  case SwitchActiveError.PocAdminNotFound(id) =>
                    NotFound(NOK.resourceNotFoundError(s"Poc admin with id '$id' not found'"))
                  case SwitchActiveError.MissingCertifyUserId(id) =>
                    Conflict(NOK.conflict(s"Poc admin '$id' does not have certifyUserId"))
                }
              case Right(_) => Ok("")
            }
        } yield r).onErrorRecover {
          case e: IllegalValueForActivateSwitch => BadRequest(NOK.badRequest(e.getMessage))
        }
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

  private def toJson[T](t: T): ActionResult = {
    Try(write[T](t)) match {
      case Success(json) => Ok(json)
      case Failure(ex) =>
        val errorMsg = s"Could not parse ${t.getClass.getSimpleName} to json"
        logger.error(errorMsg, ex)
        InternalServerError(NOK.serverError(errorMsg))
    }
  }

  private def handleValidation(tenant: Tenant, validSortColumns: Seq[String]) =
    CriteriaValidator.validateParams(tenant.id, params, validSortColumns) match {
      case Validated.Valid(a)   => Task(a)
      case Validated.Invalid(e) => Task.raiseError(ValidationError(e))
    }
}

case class AddDeviceCreationTokenRequest(token: String)

object TenantAdminController {
  case class Paginated_OUT[T](total: Long, records: Seq[T])
  case class ValidationError(n: NonEmptyChain[(String, String)]) extends RuntimeException(s"Validation errors occurred")

  case class PocAdmin_OUT(
    id: UUID,
    firstName: String,
    lastName: String,
    dateOfBirth: LocalDate,
    email: String,
    phone: String,
    pocName: String,
    state: Status,
    webIdentInitiateId: Option[UUID],
    webIdentSuccessId: Option[String]
  )

  object PocAdmin_OUT {
    def fromPocAdmin(pocAdmin: PocAdmin, poc: Poc): PocAdmin_OUT =
      PocAdmin_OUT(
        id = pocAdmin.id,
        firstName = pocAdmin.name,
        lastName = pocAdmin.surname,
        dateOfBirth = pocAdmin.dateOfBirth.date,
        email = pocAdmin.email,
        phone = pocAdmin.mobilePhone,
        pocName = poc.pocName,
        state = pocAdmin.status,
        webIdentInitiateId = pocAdmin.webIdentInitiateId,
        webIdentSuccessId = pocAdmin.webIdentId
      )
  }

  implicit class ResponseOps[T](r: Response[T]) {
    def toJson(implicit f: Formats): Task[String] = Task(write[Response[T]](r))
  }
}
