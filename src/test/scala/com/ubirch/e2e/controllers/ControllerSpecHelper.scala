package com.ubirch.e2e.controllers

import com.ubirch.InjectorHelper
import com.ubirch.ModelCreationHelper.{ createPoc, createPocAdmin, createTenant }
import com.ubirch.db.tables.{ PocAdminTable, PocTable, TenantTable }
import com.ubirch.models.poc.{ Completed, Pending, Poc, PocAdmin }
import com.ubirch.models.tenant.Tenant
import com.ubirch.services.poc.PocTestHelper.await
import monix.execution.Scheduler

import java.util.UUID
import scala.concurrent.duration.DurationInt

trait ControllerSpecHelper {
  def createTenantWithPocAndPocAdmin(injector: InjectorHelper)(implicit
  scheduler: Scheduler): (Tenant, Poc, PocAdmin) = {
    val tenant = addTenantToDB(injector)
    val poc = addPocToDb(tenant, injector)
    val pocAdmin = addPocAdminToDB(poc, tenant, injector)
    (tenant, poc, pocAdmin)
  }

  def addPocAdminToDB(poc: Poc, tenant: Tenant, injector: InjectorHelper)(implicit scheduler: Scheduler): PocAdmin = {
    val pocAdminTable = injector.get[PocAdminTable]
    val pocAdmin =
      createPocAdmin(pocId = poc.id, tenantId = tenant.id, certifyUserId = Some(UUID.randomUUID()), status = Completed)
    await(pocAdminTable.createPocAdmin(pocAdmin), 5.seconds)
    pocAdmin
  }

  def addTenantToDB(injector: InjectorHelper)(implicit scheduler: Scheduler): Tenant = {
    val tenantTable = injector.get[TenantTable]
    val tenant = createTenant()
    await(tenantTable.createTenant(tenant), 5.seconds)
    tenant
  }

  def addPocToDb(tenant: Tenant, injector: InjectorHelper)(implicit scheduler: Scheduler): Poc = {
    val pocTable = injector.get[PocTable]
    val poc = createPoc(tenantName = tenant.tenantName, status = Pending)
    await(pocTable.createPoc(poc), 5.seconds)
    poc
  }

}
