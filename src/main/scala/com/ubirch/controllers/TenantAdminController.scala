package com.ubirch.controllers

import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.ConfPaths.ServicesConfPaths.TENANT_ADMIN_ROLE
import com.ubirch.controllers.TenantAdminController._
import com.ubirch.controllers.concerns.{ControllerBase, KeycloakBearerAuthStrategy, KeycloakBearerAuthenticationSupport, Token}
import com.ubirch.db.tables.PocRepository.PocCriteria
import com.ubirch.db.tables.{PocRepository, PocStatusRepository, TenantTable}
import com.ubirch.models.NOK
import com.ubirch.models.common.{Order, Page, Sort}
import com.ubirch.models.poc.Poc
import com.ubirch.models.tenant.{Tenant, TenantGroupId}
import com.ubirch.services.DeviceKeycloak
import com.ubirch.services.jwt.{PublicKeyPoolService, TokenVerificationService}
import com.ubirch.services.poc.PocBatchHandlerImpl
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization.write
import org.scalatra._
import org.scalatra.swagger.{Swagger, SwaggerSupportSyntax}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util._

class TenantAdminController @Inject() (
  pocBatchHandler: PocBatchHandlerImpl,
  pocStatusTable: PocStatusRepository,
  pocTable: PocRepository,
  tenantTable: TenantTable,
  config: Config,
  val swagger: Swagger,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  tokenVerificationService: TokenVerificationService)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {

  implicit override protected def jsonFormats: Formats = jFormats

  override protected def applicationDescription: String = "Tenant Admin Controller"

  private val tenantAdminRole = Symbol(config.getString(TENANT_ADMIN_ROLE))
  override val service: String = config.getString(GenericConfPaths.NAME)

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, DeviceKeycloak, tokenVerificationService, publicKeyPoolService)

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
    apiOperation[String]("create list of PoCs")
      .summary("PoC batch creation")
      .description("Receives a semicolon separated .csv with a list of PoC details to create the PoCs." +
        " In case of not parsable rows, these will be returned in the answer with a specific remark.")
      .tags("PoC, Tenant-Admin")

  val getPocStatus: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("retrieve PoC Status via pocId")
      .summary("Get PoC Status")
      .description("Retrieve PoC Status queried by pocId. If it doesn't exist 404 is returned.")
      .tags("Tenant-Admin, PocStatus")

  post("/pocs/create", operation(createListOfPocs)) {

    authenticated(_.hasRole(tenantAdminRole)) { token: Token =>
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
    authenticated(_.hasRole(tenantAdminRole)) { token =>
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

  get("/pocs", operation(getPocStatus)) {
    authenticated(_.hasRole(tenantAdminRole)) { token: Token =>
      asyncResult("Get all PoCs for a tenant") { _ =>
        _ =>
          val page = Page(
            params.get("pageIndex").map(_.toInt).getOrElse(0),
            params.get("pageSize").map(_.toInt).getOrElse(20)
          )
          val sort = Sort(
            params.get("sortColumn"),
            Order.fromString(params.get("sortOrder").getOrElse("asc"))
          )
          val search = params.get("search")

          retrieveTenantFromToken(token).flatMap {
            case Right(tenant: Tenant) =>
              (for {
                pocs <- pocTable.getAllPocsByCriteria(PocCriteria(tenant.id.value, page, sort, search))
              } yield PoC_OUT(pocs.total, pocs.pocs))
                .map(toJson)
                .onErrorHandle(ex =>
                  InternalServerError(NOK.serverError(
                    s"something went wrong retrieving pocs for tenant with id ${tenant.id}" + ex.getMessage)))
          case Left(errorMsg: String) =>
            logger.error(errorMsg)
            Task(BadRequest(NOK.authenticationError(errorMsg)))
        }
      }
    }
  }

  private def retrieveTenantFromToken(token: Token): Task[Either[String, Tenant]] = {
    token.roles.find(_.name.startsWith("T_")) match {

      case Some(tenantRole) =>
        tenantTable.getTenantByGroupId(TenantGroupId(tenantRole.name)).map {
          case Some(tenant) => Right(tenant)
          case None         => Left(s"couldn't find tenant in db for ${tenantRole.name}")
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
}

object TenantAdminController {
  case class PoC_OUT(total: Long, pocs: Seq[Poc])
}
