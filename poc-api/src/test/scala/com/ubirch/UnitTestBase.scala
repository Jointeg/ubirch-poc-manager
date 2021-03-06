package com.ubirch

import com.google.inject.binder.ScopedBindingBuilder
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.db.tables._
import com.ubirch.e2e.{ DiscoveryServiceType, MockDiscoveryService, TestPostgresQuillMonixJdbcContext }
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.services.keycloak.groups.{ KeycloakGroupService, TestKeycloakGroupsService }
import com.ubirch.services.keycloak.roles.{ KeycloakRolesService, TestKeycloakRolesService }
import com.ubirch.services.keycloak.users.{ KeycloakUserService, TestKeycloakUserService }
import com.ubirch.services.poc.{
  CertHandler,
  DeviceCreator,
  DeviceCreatorMockSuccess,
  InformationProvider,
  InformationProviderMockSuccess,
  TestCertHandler
}
import com.ubirch.services.teamdrive.model.TeamDriveClient
import com.ubirch.test.FakeTeamDriveClient
import org.scalatest.{ EitherValues, OptionValues }
import org.scalatra.test.scalatest.ScalatraWordSpec

trait UnitTestBase
  extends ScalatraWordSpec
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues {

  def withInjector[A](testCode: UnitTestInjectorHelper => A): A = {
    testCode(new UnitTestInjectorHelper())
  }

}

class UnitTestInjectorHelper() extends InjectorHelper(List(new DefaultUnitTestBinder))

class DefaultUnitTestBinder extends Binder {
  override def PublicKeyPoolService: ScopedBindingBuilder =
    bind(classOf[PublicKeyPoolService]).to(classOf[FakeDefaultPublicKeyPoolService])

  override def UserRepository: ScopedBindingBuilder =
    bind(classOf[UserRepository]).to(classOf[UserRepositoryMock])

  override def QuillMonixJdbcContext: ScopedBindingBuilder =
    bind(classOf[QuillMonixJdbcContext]).to(classOf[TestPostgresQuillMonixJdbcContext])

  override def PocRepository: ScopedBindingBuilder =
    bind(classOf[PocRepository]).to(classOf[PocRepositoryMock])

  override def PocStatusRepository: ScopedBindingBuilder =
    bind(classOf[PocStatusRepository]).to(classOf[PocStatusRepositoryMock])

  override def PocAdminRepository: ScopedBindingBuilder =
    bind(classOf[PocAdminRepository]).to(classOf[PocAdminRepositoryMock])

  override def PocAdminStatusRepository: ScopedBindingBuilder =
    bind(classOf[PocAdminStatusRepository]).to(classOf[PocAdminStatusRepositoryMock])

  override def PocEmployeeRepository: ScopedBindingBuilder =
    bind(classOf[PocEmployeeRepository]).to(classOf[PocEmployeeRepositoryMock])

  override def PocEmployeeStatusRepository: ScopedBindingBuilder =
    bind(classOf[PocEmployeeStatusRepository]).to(classOf[PocEmployeeStatusRepositoryMock])

  override def TenantRepository: ScopedBindingBuilder =
    bind(classOf[TenantRepository]).to(classOf[TenantRepositoryMock])

  override def PocLogoRepository: ScopedBindingBuilder =
    bind(classOf[PocLogoRepository]).to(classOf[PocLogoRepositoryMock])

  override def KeycloakGroupService: ScopedBindingBuilder =
    bind(classOf[KeycloakGroupService]).to(classOf[TestKeycloakGroupsService])

  override def KeycloakRolesService: ScopedBindingBuilder =
    bind(classOf[KeycloakRolesService]).to(classOf[TestKeycloakRolesService])

  override def KeycloakUserService: ScopedBindingBuilder =
    bind(classOf[KeycloakUserService]).to(classOf[TestKeycloakUserService])

  override def DeviceCreator: ScopedBindingBuilder =
    bind(classOf[DeviceCreator]).to(classOf[DeviceCreatorMockSuccess])

  override def InformationProvider: ScopedBindingBuilder =
    bind(classOf[InformationProvider]).to(classOf[InformationProviderMockSuccess])

  override def CertHandler: ScopedBindingBuilder =
    bind(classOf[CertHandler]).to(classOf[TestCertHandler])

  override def TeamDriveClient: ScopedBindingBuilder =
    bind(classOf[TeamDriveClient]).to(classOf[FakeTeamDriveClient])

  override def configure(): Unit = {
    super.configure()
    bind(classOf[DiscoveryServiceType]).toInstance(MockDiscoveryService)
  }
}
