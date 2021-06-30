package com.ubirch.e2e.controllers

import com.ubirch.InjectorHelper
import com.ubirch.ModelCreationHelper._
import com.ubirch.data.KeycloakTestData
import com.ubirch.db.tables.{ PocAdminTable, PocEmployeeTable, PocTable, TenantTable }
import com.ubirch.models.keycloak.user.UserException
import com.ubirch.models.poc.{ Completed, Pending, Poc, PocAdmin }
import com.ubirch.models.pocEmployee.PocEmployee
import com.ubirch.models.tenant.Tenant
import com.ubirch.models.user.UserId
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.PocTestHelper.await
import com.ubirch.test.TestData
import com.ubirch.util.KeycloakRealmsHelper._
import monix.eval.Task
import monix.execution.Scheduler

import java.util.UUID
import scala.concurrent.duration.DurationInt

trait ControllerSpecHelper {
  def addTenantWithPocAndPocAdminToTable(
    injector: InjectorHelper,
    tenantName: String = globalTenantName,
    adminCertifyUserId: Option[UUID] = Some(UUID.randomUUID()))(implicit
  scheduler: Scheduler): (Tenant, Poc, PocAdmin) = {
    val tenant = addTenantToDB(injector, tenantName)
    val (poc: Poc, pocAdmin: PocAdmin) = addPocAndPocAdminToTable(injector, tenant, adminCertifyUserId)
    (tenant, poc, pocAdmin)
  }

  def addPocAndPocAdminToTable(
    injector: InjectorHelper,
    tenant: Tenant,
    adminCertifyUserId: Option[UUID] = Some(UUID.randomUUID()))(implicit scheduler: Scheduler): (Poc, PocAdmin) = {
    val poc = addPocToDb(tenant, injector)
    val pocAdmin = addPocAdminToDB(poc, tenant, injector, adminCertifyUserId)
    (poc, pocAdmin)
  }

  def addPocAdminToDB(
    poc: Poc,
    tenant: Tenant,
    injector: InjectorHelper,
    certifyUserId: Option[UUID] = Some(UUID.randomUUID()))(implicit scheduler: Scheduler): PocAdmin = {
    val pocAdminTable = injector.get[PocAdminTable]
    val pocAdmin =
      createPocAdmin(
        pocId = poc.id,
        tenantId = tenant.id,
        certifyUserId = certifyUserId,
        status = Completed)
    await(pocAdminTable.createPocAdmin(pocAdmin))
    pocAdmin
  }

  def addTenantToDB(injector: InjectorHelper, tenantName: String = globalTenantName)(implicit
  scheduler: Scheduler): Tenant = {
    val tenantTable = injector.get[TenantTable]
    val tenant = createTenant(tenantName)
    await(tenantTable.createTenant(tenant))
    tenant
  }

  def addPocToDb(tenant: Tenant, injector: InjectorHelper)(implicit scheduler: Scheduler): Poc = {
    val pocTable = injector.get[PocTable]
    val poc = createPoc(tenantName = tenant.tenantName, status = Pending)
    await(pocTable.createPoc(poc))
    poc
  }

  def addPocEmployeeToDb(tenant: Tenant, poc: Poc, injector: InjectorHelper)(implicit
  scheduler: Scheduler): PocEmployee = {
    val employeeTable = injector.get[PocEmployeeTable]
    val employee = createPocEmployee(tenantId = tenant.id, pocId = poc.id)
    await(employeeTable.createPocEmployee(employee))
    employee
  }

  def createKeycloakUserForPocEmployee(
    keycloakUserService: KeycloakUserService,
    p: Poc): Task[Either[UserException, UserId]] = {
    keycloakUserService.createUserWithoutUserName(
      p.getRealm,
      KeycloakTestData.createNewCertifyKeycloakUser(),
      CertifyKeycloak)
  }
}
