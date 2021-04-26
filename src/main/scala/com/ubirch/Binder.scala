package com.ubirch

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.db.context.{ PostgresQuillJdbcContext, QuillJdbcContext }
import com.ubirch.db.tables.{ UserRepository, UserTable }
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.{ ExecutionProvider, SchedulerProvider }
import com.ubirch.services.formats.{ DefaultJsonConverterService, JsonConverterService, JsonFormatsProvider }
import com.ubirch.services.jwt._
import com.ubirch.services.keycloak.auth.{ AuthClient, KeycloakAuthzClient }
import com.ubirch.services.keycloak.groups.{ DefaultKeycloakGroupService, KeycloakGroupService }
import com.ubirch.services.keycloak.roles.{ DefaultKeycloakRolesService, KeycloakRolesService }
import com.ubirch.services.keycloak.users.{
  KeycloakUserPollingService,
  KeycloakUserService,
  KeycloakUserServiceImpl,
  UserPollingService
}
import com.ubirch.services.keycloak.{ KeycloakConfig, KeycloakConfigConnector, KeycloakConnector, RealKeycloakConfig }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.rest.SwaggerProvider
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.Swagger

import scala.concurrent.ExecutionContext

class Binder extends AbstractModule {

  def Config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])
  def KeycloakConfig: ScopedBindingBuilder = bind(classOf[KeycloakConfig]).to(classOf[RealKeycloakConfig])
  def ExecutionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])
  def Scheduler: ScopedBindingBuilder = bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])
  def Swagger: ScopedBindingBuilder = bind(classOf[Swagger]).toProvider(classOf[SwaggerProvider])
  def Formats: ScopedBindingBuilder = bind(classOf[Formats]).toProvider(classOf[JsonFormatsProvider])
  def Lifecycle: ScopedBindingBuilder = bind(classOf[Lifecycle]).to(classOf[DefaultLifecycle])
  def JVMHook: ScopedBindingBuilder = bind(classOf[JVMHook]).to(classOf[DefaultJVMHook])
  def JsonConverterService: ScopedBindingBuilder =
    bind(classOf[JsonConverterService]).to(classOf[DefaultJsonConverterService])
  def TokenCreationService: ScopedBindingBuilder =
    bind(classOf[TokenCreationService]).to(classOf[DefaultTokenCreationService])
  def TokenVerificationService: ScopedBindingBuilder =
    bind(classOf[TokenVerificationService]).to(classOf[DefaultTokenVerificationService])
  def PublicKeyDiscoveryService: ScopedBindingBuilder =
    bind(classOf[PublicKeyDiscoveryService]).to(classOf[DefaultPublicKeyDiscoveryService])
  def PublicKeyPoolService: ScopedBindingBuilder =
    bind(classOf[PublicKeyPoolService]).to(classOf[DefaultPublicKeyPoolService])
  def KeycloakConnector: ScopedBindingBuilder =
    bind(classOf[KeycloakConnector]).to(classOf[KeycloakConfigConnector])
  def KeycloakUserService: ScopedBindingBuilder =
    bind(classOf[KeycloakUserService]).to(classOf[KeycloakUserServiceImpl])
  def KeycloakRolesService: ScopedBindingBuilder =
    bind(classOf[KeycloakRolesService]).to(classOf[DefaultKeycloakRolesService])
  def KeycloakGroupService: ScopedBindingBuilder =
    bind(classOf[KeycloakGroupService]).to(classOf[DefaultKeycloakGroupService])
  def AuthClient: ScopedBindingBuilder =
    bind(classOf[AuthClient]).to(classOf[KeycloakAuthzClient])
  def UserPollingService: ScopedBindingBuilder =
    bind(classOf[UserPollingService]).to(classOf[KeycloakUserPollingService])
  def QuillJdbcContext: ScopedBindingBuilder =
    bind(classOf[QuillJdbcContext]).to(classOf[PostgresQuillJdbcContext])
  def UserRepository: ScopedBindingBuilder =
    bind(classOf[UserRepository]).to(classOf[UserTable])

  override def configure(): Unit = {
    Config
    KeycloakConfig
    ExecutionContext
    Scheduler
    Swagger
    Formats
    Lifecycle
    JVMHook
    JsonConverterService
    TokenCreationService
    TokenVerificationService
    PublicKeyDiscoveryService
    PublicKeyPoolService
    KeycloakConnector
    KeycloakUserService
    KeycloakRolesService
    KeycloakGroupService
    AuthClient
    UserPollingService
    QuillJdbcContext
    UserRepository
    ()
  }
}

object Binder {
  def modules: List[Module] = List(new Binder)
}
