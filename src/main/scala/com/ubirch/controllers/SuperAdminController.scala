package com.ubirch.controllers

import com.google.inject.Singleton
import com.typesafe.config.Config
import com.ubirch.ConfPaths.GenericConfPaths
import com.ubirch.controllers.concerns.{
  ControllerBase,
  KeycloakBearerAuthStrategy,
  KeycloakBearerAuthenticationSupport,
  Token
}
import com.ubirch.models.NOK
import com.ubirch.models.keycloak.user.CreateKeycloakUser
import com.ubirch.models.user.{ Email, FirstName, LastName, UserName }
import com.ubirch.services.jwt.{ PublicKeyPoolService, TokenVerificationService }
import com.ubirch.services.keycloak.users.KeycloakUserService
import io.prometheus.client.Counter
import monix.eval.Task
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.{ Swagger, SwaggerSupportSyntax }
import org.scalatra.{ NotFound, Ok, ScalatraBase }

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Random

@Singleton
class SuperAdminController @Inject() (
  config: Config,
  val swagger: Swagger,
  jFormats: Formats,
  publicKeyPoolService: PublicKeyPoolService,
  tokenVerificationService: TokenVerificationService,
  keycloakUserService: KeycloakUserService)(implicit val executor: ExecutionContext, scheduler: Scheduler)
  extends ControllerBase
  with KeycloakBearerAuthenticationSupport {
  override protected val applicationDescription: String = "Super Admin controller"

  implicit override protected def jsonFormats: Formats = jFormats

  override val service: String = config.getString(GenericConfPaths.NAME)

  override protected def createStrategy(app: ScalatraBase): KeycloakBearerAuthStrategy =
    new KeycloakBearerAuthStrategy(app, tokenVerificationService, publicKeyPoolService)

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

  val getSimpleCheck: SwaggerSupportSyntax.OperationBuilder =
    apiOperation[String]("initialTest")
      .summary("Test")
      .description("Getting a test message from system")
      .tags("Test")

  get("/initialTest", operation(getSimpleCheck)) {
    authenticated() { token =>
      asyncResult("initialTest") { _ => _ =>
        Task(test(token))
      }
    }
  }

  get("/simpleTest") {
    asyncResult("simpleTest") { _ => _ =>
      keycloakUserService
        .createUser(
          CreateKeycloakUser(
            FirstName(Random.alphanumeric.take(10).mkString("")),
            LastName(Random.alphanumeric.take(10).mkString("")),
            UserName(s"${Random.alphanumeric.take(10).mkString("")}@test.com"),
            Email(s"${Random.alphanumeric.take(10).mkString("")}@test.com")
          )
        )
        .map(_ => Ok("Created"))
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

  private def test(token: Token) = {
    Ok(s"Test successful for ${token.name}")
  }
}
