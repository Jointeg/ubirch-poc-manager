package com.ubirch.controllers

import cats.data.{ NonEmptyChain, Validated }
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Token
}
import com.ubirch.controllers.validator.CriteriaValidator
import com.ubirch.db.tables.{ PocAdminRepository, PocRepository, PocStatusRepository, TenantTable }
import com.ubirch.models.poc.{ Poc, PocAdmin, PocStatus, Status }
import com.ubirch.models.tenant.{
  CreateWebIdentInitiateIdRequest,
  CreateWebInitiateIdResponse,
  Tenant,
  TenantName,
  UpdateWebIdentIdRequest
}
import com.ubirch.models.{ NOK, Response, ValidationErrorsResponse }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.poc.PocBatchHandlerImpl
import com.ubirch.services.tenantadmin._
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
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

  post("/pocs/create", operation(createListOfPocs)) {

    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token: Token =>
      asyncResult("Create poc batch") { _ => _ =>
        retrieveTenantFromToken(token).flatMap {
          case Right(tenant: Tenant) =>
            pocBatchHandler
              .createListOfPoCs(request.body, tenant)
              .map {
                case Right(_)  => Ok()
                case Left(csv) => Ok(csv)
              }
          case Left(errorMsg: String) =>
            logger.error(errorMsg)
            Task(BadRequest(NOK.authenticationError(errorMsg)))
        }
      }
    }
  }

  get("/pocStatus/:id", operation(getPocStatus)) {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { _ =>
      asyncResult("Get Poc Status") { _ => _ =>
        val id = params("id")
        Try(UUID.fromString(id)) match {
          case Success(uuid) =>
            pocStatusTable
              .getPocStatus(uuid)
              .map {
                case Some(pocStatus) => toJson(pocStatus)
                case None            => NotFound(NOK.resourceNotFoundError(s"pocStatus with $id couldn't be found"))
              }
          case Failure(ex) =>
            val errorMsg = s"error on retrieving pocStatus with $id:"
            logger.error(errorMsg, ex)
            Task(InternalServerError(NOK.serverError(errorMsg + ex.getMessage)))

        }
      }
    }
  }

  get("/pocs", operation(getPocs)) {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token: Token =>
      asyncResult("Get all PoCs for a tenant") { _ => _ =>
        retrieveTenantFromToken(token).flatMap {
          case Right(tenant: Tenant) =>
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
          case Left(errorMsg: String) =>
            logger.error(errorMsg)
            Task(BadRequest(NOK.authenticationError(errorMsg)))
        }
      }
    }
  }

  get("/devices", operation(getDevices)) {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token: Token =>
      asyncResult("Get all Devices for all PoCs of tenant") { _ => _ =>
        retrieveTenantFromToken(token).flatMap {
          case Right(tenant: Tenant) =>
            tenantAdminService.getSimplifiedDeviceInfoAsCSV(tenant).map(devicesInformation => {
              response.setContentType("text/csv")
              response.setHeader("Content-Disposition", "attachment; filename=simplified-devices-info.csv")
              Ok(devicesInformation)
            })
          case Left(errorMsg: String) =>
            logger.error(errorMsg)
            Task(BadRequest(NOK.authenticationError(errorMsg)))
        }
      }
    }
  }

  post("/webident/initiate-id", operation(createWebInitiateId)) {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token: Token =>
      asyncResult("Create web initiate id") { _ => _ =>
        retrieveTenantFromToken(token).flatMap {
          case Right(tenant: Tenant) =>
            tenantAdminService.createWebIdentInitiateId(
              tenant,
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
            }
          case Left(errorMsg: String) =>
            logger.error(errorMsg)
            Task(BadRequest(NOK.authenticationError(errorMsg)))
        }
      }
    }
  }

  post("/webident/id", operation(updateWebIdentId)) {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token: Token =>
      asyncResult("Update Webident identifier") { _ => _ =>
        retrieveTenantFromToken(token).flatMap {
          case Right(tenant: Tenant) =>
            import UpdateWebIdentIdError._
            tenantAdminService.updateWebIdentId(
              tenant,
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
          case Left(errorMsg: String) =>
            logger.error(errorMsg)
            Task(BadRequest(NOK.authenticationError(errorMsg)))
        }
      }
    }
  }

  get("/poc-admins", operation(getPocAdmins)) {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token: Token =>
      asyncResult("Get all PoC Admins for a tenant") { _ => _ =>
        retrieveTenantFromToken(token).flatMap {
          case Right(tenant: Tenant) =>
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
          case Left(errorMsg: String) =>
            logger.error(errorMsg)
            Task(BadRequest(NOK.authenticationError(errorMsg)))
        }
      }
    }
  }

  put("/poc-admin/:id/active/:isActive") {
    authenticated(_.hasRole(Token.TENANT_ADMIN)) { token =>
      Ok("")
    }
  }

  private def retrieveTenantFromToken(token: Token): Task[Either[String, Tenant]] = {

    token.roles.find(_.name.startsWith(TENANT_GROUP_PREFIX)) match {
      case Some(roleName) =>
        tenantTable.getTenantByName(TenantName(roleName.name.stripPrefix(TENANT_GROUP_PREFIX))).map {
          case Some(tenant) => Right(tenant)
          case None         => Left(s"couldn't find tenant in db for ${roleName.name}")
        }
      case None => Task(Left("the user's token is missing a tenant role"))
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
    webIdentInitiateId: Option[UUID]
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
        webIdentInitiateId = pocAdmin.webIdentInitiateId
      )
  }

  implicit class ResponseOps[T](r: Response[T]) {
    def toJson(implicit f: Formats): Task[String] = Task(write[Response[T]](r))
  }
}
