package com.ubirch

import com.google.inject.binder.ScopedBindingBuilder
import com.google.inject.{ AbstractModule, Module }
import com.typesafe.config.Config
import com.ubirch.controllers.concerns.{ X509CertSupport, X509CertSupportImpl }
import com.ubirch.db.context.{ PostgresQuillMonixJdbcContext, QuillMonixJdbcContext }
import com.ubirch.db.tables._
import com.ubirch.db.{ FlywayProvider, FlywayProviderImpl }
import com.ubirch.services.auth._
import com.ubirch.services.clock.ClockProvider
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.{ ExecutionProvider, SchedulerProvider }
import com.ubirch.services.formats.{
  CustomFormats,
  CustomJsonFormats,
  DefaultJsonConverterService,
  JsonConverterService,
  JsonFormatsProvider
}
import com.ubirch.services.healthcheck.{ DefaultHealthCheckService, HealthCheckService }
import com.ubirch.services.jwt._
import com.ubirch.services.keycloak._
import com.ubirch.services.keycloak.groups.{ DefaultKeycloakGroupService, KeycloakGroupService }
import com.ubirch.services.keycloak.roles.{ DefaultKeycloakRolesService, KeycloakRolesService }
import com.ubirch.services.keycloak.users.{ DefaultKeycloakUserService, KeycloakUserService }
import com.ubirch.services.keyhash.{ DefaultKeyHashVerifier, KeyHashVerifierService }
import com.ubirch.services.lifecycle.{ DefaultJVMHook, DefaultLifecycle, JVMHook, Lifecycle }
import com.ubirch.services.poc._
import com.ubirch.services.poc.employee.{
  CsvProcessPocEmployee,
  CsvProcessPocEmployeeImpl,
  PocEmployeeService,
  PocEmployeeServiceImpl
}
import com.ubirch.services.poc.util.{ ImageLoader, ImageLoaderImpl }
import com.ubirch.services.pocadmin.{ PocAdminService, PocAdminServiceImpl }
import com.ubirch.services.rest.SwaggerProvider
import com.ubirch.services.superadmin.{
  DefaultSuperAdminService,
  SuperAdminService,
  TenantKeycloakHelper,
  TenantKeycloakHelperImpl
}
import com.ubirch.services.teamdrive.model.TeamDriveClient
import com.ubirch.services.teamdrive._
import com.ubirch.services.tenantadmin.{ DefaultTenantAdminService, TenantAdminService }
import com.ubirch.services.{ DefaultKeycloakConnector, KeycloakConnector }
import monix.execution.Scheduler
import org.json4s.Formats
import org.scalatra.swagger.Swagger

import java.time.Clock
import scala.concurrent.ExecutionContext

class Binder extends AbstractModule {

  def Config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(classOf[ConfigProvider])

  def Clock: ScopedBindingBuilder = bind(classOf[Clock]).toProvider(classOf[ClockProvider])

  def PocConfig: ScopedBindingBuilder = bind(classOf[PocConfig]).to(classOf[PocConfigImpl])

  def KeycloakUsersConfig: ScopedBindingBuilder =
    bind(classOf[KeycloakCertifyConfig]).to(classOf[RealKeycloakCertifyConfig])

  def KeycloakDeviceConfig: ScopedBindingBuilder =
    bind(classOf[KeycloakDeviceConfig]).to(classOf[RealKeycloakDeviceConfig])

  def ExecutionContext: ScopedBindingBuilder = bind(classOf[ExecutionContext]).toProvider(classOf[ExecutionProvider])

  def Scheduler: ScopedBindingBuilder = bind(classOf[Scheduler]).toProvider(classOf[SchedulerProvider])

  def Swagger: ScopedBindingBuilder = bind(classOf[Swagger]).toProvider(classOf[SwaggerProvider])

  def CustomFormats: ScopedBindingBuilder = bind(classOf[CustomJsonFormats]).to(classOf[CustomFormats])

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

  def SuperAdminService: ScopedBindingBuilder =
    bind(classOf[SuperAdminService]).to(classOf[DefaultSuperAdminService])

  def TenantKeycloakHelper: ScopedBindingBuilder =
    bind(classOf[TenantKeycloakHelper]).to(classOf[TenantKeycloakHelperImpl])

  def KeycloakUserConnector: ScopedBindingBuilder =
    bind(classOf[CertifyKeycloakConnector]).to(classOf[CertifyKeycloakConfigConnector])

  def KeycloakDeviceConnector: ScopedBindingBuilder =
    bind(classOf[DeviceKeycloakConnector]).to(classOf[DeviceKeycloakConfigConnector])

  def KeycloakConnector: ScopedBindingBuilder =
    bind(classOf[KeycloakConnector]).to(classOf[DefaultKeycloakConnector])

  def KeycloakUserService: ScopedBindingBuilder =
    bind(classOf[KeycloakUserService]).to(classOf[DefaultKeycloakUserService])

  def KeycloakRolesService: ScopedBindingBuilder =
    bind(classOf[KeycloakRolesService]).to(classOf[DefaultKeycloakRolesService])

  def KeycloakGroupService: ScopedBindingBuilder =
    bind(classOf[KeycloakGroupService]).to(classOf[DefaultKeycloakGroupService])

  def QuillMonixJdbcContext: ScopedBindingBuilder =
    bind(classOf[QuillMonixJdbcContext]).to(classOf[PostgresQuillMonixJdbcContext])

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

  def PocBatchHandler: ScopedBindingBuilder = bind(classOf[PocBatchHandlerTrait]).to(classOf[PocBatchHandlerImpl])

  def CsvProcessPoc: ScopedBindingBuilder = bind(classOf[CsvProcessPoc]).to(classOf[CsvProcessPocImpl])

  def CsvProcessPocAdmin: ScopedBindingBuilder = bind(classOf[CsvProcessPocAdmin]).to(classOf[CsvProcessPocAdminImpl])

  def PocRepository: ScopedBindingBuilder = bind(classOf[PocRepository]).to(classOf[PocTable])

  def PocStatusRepository: ScopedBindingBuilder = bind(classOf[PocStatusRepository]).to(classOf[PocStatusTable])

  def TenantAdminService: ScopedBindingBuilder =
    bind(classOf[TenantAdminService]).to(classOf[DefaultTenantAdminService])

  def PocAdminRepository: ScopedBindingBuilder = bind(classOf[PocAdminRepository]).to(classOf[PocAdminTable])

  def PocAdminStatusRepository: ScopedBindingBuilder =
    bind(classOf[PocAdminStatusRepository]).to(classOf[PocAdminStatusTable])

  def PocEmployeeRepository: ScopedBindingBuilder = bind(classOf[PocEmployeeRepository]).to(classOf[PocEmployeeTable])

  def PocEmployeeStatusRepository: ScopedBindingBuilder =
    bind(classOf[PocEmployeeStatusRepository]).to(classOf[PocEmployeeStatusTable])

  def PocLogoRepository: ScopedBindingBuilder =
    bind(classOf[PocLogoRepository]).to(classOf[PocLogoTable])

  def FlywayProvider: ScopedBindingBuilder = bind(classOf[FlywayProvider]).to(classOf[FlywayProviderImpl])

  def DeviceCreator: ScopedBindingBuilder = bind(classOf[DeviceCreator]).to(classOf[DeviceCreatorImpl])

  def DeviceHelper: ScopedBindingBuilder = bind(classOf[DeviceHelper]).to(classOf[DeviceHelperImpl])

  def KeycloakHelper: ScopedBindingBuilder = bind(classOf[KeycloakHelper]).to(classOf[KeycloakHelperImpl])

  def InformationProvider: ScopedBindingBuilder =
    bind(classOf[InformationProvider]).to(classOf[InformationProviderImpl])

  def PocCreator: ScopedBindingBuilder = bind(classOf[PocCreator]).to(classOf[PocCreatorImpl])

  def PocCreationLoop: ScopedBindingBuilder = bind(classOf[PocCreationLoop]).to(classOf[PocCreationLoopImpl])

  def PocAdminCreator: ScopedBindingBuilder = bind(classOf[PocAdminCreator]).to(classOf[PocAdminCreatorImpl])

  def PocAdminCreationLoop: ScopedBindingBuilder =
    bind(classOf[PocAdminCreationLoop]).to(classOf[PocAdminCreationLoopImpl])

  def AdminCertifyHelper: ScopedBindingBuilder = bind(classOf[AdminCertifyHelper]).to(classOf[AdminCertifyHelperImpl])

  def PocEmployeeCreator: ScopedBindingBuilder = bind(classOf[PocEmployeeCreator]).to(classOf[PocEmployeeCreatorImpl])

  def PocEmployeeCreationLoop: ScopedBindingBuilder =
    bind(classOf[PocEmployeeCreationLoop]).to(classOf[PocEmployeeCreationLoopImpl])

  def PocAdminService: ScopedBindingBuilder =
    bind(classOf[PocAdminService]).to(classOf[PocAdminServiceImpl])

  def EmployeeCertifyHelper: ScopedBindingBuilder =
    bind(classOf[EmployeeCertifyHelper]).to(classOf[EmployeeCertifyHelperImpl])

  def CertHandler: ScopedBindingBuilder = bind(classOf[CertHandler]).to(classOf[CertCreatorImpl])

  def TeamDriveClient: ScopedBindingBuilder = bind(classOf[TeamDriveClient]).to(classOf[SttpTeamDriveClient])

  def TeamDriverService: ScopedBindingBuilder = bind(classOf[TeamDriveService]).to(classOf[TeamDriveServiceImpl])

  def TeamDriveClientConfig: ScopedBindingBuilder =
    bind(classOf[TeamDriveClientConfig]).to(classOf[TypesafeTeamDriveClientConfig])

  def CsvProcessPocEmployee: ScopedBindingBuilder =
    bind(classOf[CsvProcessPocEmployee]).to(classOf[CsvProcessPocEmployeeImpl])

  def ImageLoader: ScopedBindingBuilder =
    bind(classOf[ImageLoader]).to(classOf[ImageLoaderImpl])

  def PocEmployeeService: ScopedBindingBuilder =
    bind(classOf[PocEmployeeService]).to(classOf[PocEmployeeServiceImpl])

  def HealthCheckRepository: ScopedBindingBuilder =
    bind(classOf[HealthCheckRepository]).to(classOf[DefaultHealthCheckRepository])

  def HealthCheckService: ScopedBindingBuilder =
    bind(classOf[HealthCheckService]).to(classOf[DefaultHealthCheckService])

  def X509CertSupport: ScopedBindingBuilder =
    bind(classOf[X509CertSupport]).to(classOf[X509CertSupportImpl])

  override def configure(): Unit = {
    Config
    Clock
    PocConfig
    KeycloakUsersConfig
    KeycloakDeviceConfig
    ExecutionContext
    Scheduler
    Swagger
    CustomFormats
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
    SuperAdminService
    TenantKeycloakHelper
    QuillMonixJdbcContext
    UserRepository
    PocBatchHandler
    CsvProcessPoc
    CsvProcessPocAdmin
    PocRepository
    PocStatusRepository
    PocAdminRepository
    PocAdminStatusRepository
    PocEmployeeRepository
    PocEmployeeStatusRepository
    PocLogoRepository
    TenantRepository
    AESKeyProvider
    AESEncryption
    FlywayProvider
    DeviceCreator
    DeviceHelper
    KeycloakHelper
    InformationProvider
    KeyHashVerifier
    HashingService
    HashKeyRepository
    TenantAdminService
    PocCreator
    PocCreationLoop
    PocAdminCreator
    PocAdminCreationLoop
    AdminCertifyHelper
    PocEmployeeCreator
    PocEmployeeCreationLoop
    PocAdminService
    EmployeeCertifyHelper
    CertHandler
    TeamDriveClient
    TeamDriverService
    TeamDriveClientConfig
    CsvProcessPocEmployee
    ImageLoader
    PocEmployeeService
    HealthCheckRepository
    HealthCheckService
    X509CertSupport
    ()
  }
}

object Binder {
  def modules: List[Module] = List(new Binder)
}
