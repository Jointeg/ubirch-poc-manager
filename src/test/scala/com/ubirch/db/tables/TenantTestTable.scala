package com.ubirch.db.tables
import com.ubirch.models.tenant.{Tenant, TenantId}
import monix.eval.Task

import scala.collection.mutable

class TenantTestTable extends TenantRepository {
  private val tenantDatastore = mutable.Map[TenantId, Tenant]()

  override def createTenant(tenant: Tenant): Task[Unit] =
    Task {
      tenantDatastore += ((tenant.id, tenant))
      ()
    }
}
