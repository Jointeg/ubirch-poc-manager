package com.ubirch

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.db.context.{ PostgresQuillJdbcContext, QuillJdbcContext }
import com.ubirch.db.tables._
import com.ubirch.db.{ FlywayProvider, FlywayProviderImpl }
import com.ubirch.services.auth._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.{ ExecutionProvider, SchedulerProvider }
import com.ubirch.services.formats.{ DefaultJsonConverterService, JsonConverterService, JsonFormatsProvider }
import com.ubirch.services.jwt._
import com.ubirch.services.keycloak._
import com.ubirch.services.keycloak.auth.{ AuthClient, KeycloakAuthzClient }
import com.ubirch.services.keycloak.groups.{ DefaultKeycloakGroupService, KeycloakGroupService }
import com.ubirch.services.keycloak.roles.{ DefaultKeycloakRolesService, KeycloakRolesService }
import com.ubirch.services.keycloak.users.{
  KeycloakUserPollingService,
  KeycloakUserService,
  KeycloakUserServiceImpl,
  UserPollingService
}
import com.ubirch.services.keyhash.{ DefaultKeyHashVerifier, KeyHashVerifierService }
import com.ubirch.services.lifeCycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.poc._
import com.ubirch.services.rest.SwaggerProvider
import com.ubirch.services.superadmin.{ DefaultTenantService, TenantService }
import com.ubirch.services.tenantadmin.{ DefaultTenantAdminService, TenantAdminService }
import com.ubirch.services.{ DefaultKeycloakConnector, KeycloakConnector }
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.Swagger

import scala.concurrent.ExecutionContext

class Binder extends AbstractModule {

  def Config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])

  def KeycloakUsersConfig: ScopedBindingBuilder =
    bind(classOf[KeycloakUsersConfig]).to(classOf[RealKeycloakUsersConfig])

  def KeycloakDeviceConfig: ScopedBindingBuilder =
    bind(classOf[KeycloakDeviceConfig]).to(classOf[RealKeycloakDeviceConfig])

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

  def TenantService: ScopedBindingBuilder =
    bind(classOf[TenantService]).to(classOf[DefaultTenantService])

  def KeycloakUserConnector: ScopedBindingBuilder =
    bind(classOf[UsersKeycloakConnector]).to(classOf[UsersKeycloakConfigConnector])

  def KeycloakDeviceConnector: ScopedBindingBuilder =
    bind(classOf[DeviceKeycloakConnector]).to(classOf[DeviceKeycloakConfigConnector])

  def KeycloakConnector: ScopedBindingBuilder =
    bind(classOf[KeycloakConnector]).to(classOf[DefaultKeycloakConnector])

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
  def TenantRepository: ScopedBindingBuilder =
    bind(classOf[TenantRepository]).to(classOf[TenantTable])

  def AESKeyProvider: ScopedBindingBuilder =
    bind(classOf[AESKeyProvider]).to(classOf[ConfigKeyProvider])

  def AESEncryption: ScopedBindingBuilder =
    bind(classOf[AESEncryption]).to(classOf[AESEncryptionCBCMode])

  def HashingService: ScopedBindingBuilder =
    bind(classOf[HashingService]).to(classOf[DefaultHashingService])

  def HashKeyRepository: ScopedBindingBuilder = bind(classOf[KeyHashRepository]).to(classOf[KeyHashTable])

  def KeyHashVerifier: ScopedBindingBuilder =
    bind(classOf[KeyHashVerifierService]).to(classOf[DefaultKeyHashVerifier])

  def PocCreatorService: ScopedBindingBuilder = bind(classOf[PocBatchHandlerTrait]).to(classOf[PocBatchHandlerImpl])

  def PocRepository: ScopedBindingBuilder = bind(classOf[PocRepository]).to(classOf[PocTable])

  def PocStatusRepository: ScopedBindingBuilder = bind(classOf[PocStatusRepository]).to(classOf[PocStatusTable])

  def TenantAdminService: ScopedBindingBuilder =
    bind(classOf[TenantAdminService]).to(classOf[DefaultTenantAdminService])

  def FlywayProvider: ScopedBindingBuilder = bind(classOf[FlywayProvider]).to(classOf[FlywayProviderImpl])

  def DeviceCreator: ScopedBindingBuilder = bind(classOf[DeviceCreator]).to(classOf[DeviceCreatorImpl])

  def KeycloakHelper: ScopedBindingBuilder = bind(classOf[KeycloakHelper]).to(classOf[KeycloakHelperImpl])

  def InformationProvider: ScopedBindingBuilder =
    bind(classOf[InformationProvider]).to(classOf[InformationProviderImpl])

  def PocCreationLoop: ScopedBindingBuilder = bind(classOf[PocCreationLoop]).to(classOf[PocCreationLoopImpl])

  override def configure(): Unit = {
    Config
    KeycloakUsersConfig
    KeycloakDeviceConfig
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
    KeycloakUserConnector
    KeycloakDeviceConnector
    KeycloakConnector
    KeycloakUserService
    KeycloakRolesService
    KeycloakGroupService
    TenantService
    AuthClient
    UserPollingService
    QuillJdbcContext
    UserRepository
    PocCreatorService
    PocRepository
    PocStatusRepository
    TenantRepository
    AESKeyProvider
    AESEncryption
    FlywayProvider
    DeviceCreator
    KeycloakHelper
    InformationProvider
    KeyHashVerifier
    HashingService
    HashKeyRepository
    TenantAdminService
    PocCreationLoop
    ()
  }
}

object Binder {
  def modules: List[Module] = List(new Binder)
}
