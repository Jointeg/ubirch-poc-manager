package com.ubirch.db.tables
import com.ubirch.models.tenant.{ Tenant, TenantGroupId, TenantId, TenantName }
import com.ubirch.models.tenant.{ Tenant, TenantId, TenantName }
import monix.eval.Task

import scala.collection.mutable

class TenantTestTable extends TenantRepository {
  private val tenantDatastore = mutable.Map[TenantId, Tenant]()

  override def createTenant(tenant: Tenant): Task[TenantId] =
    Task {
      tenantDatastore += ((tenant.id, tenant))
      tenant.id
    }

  override def getTenant(tenantId: TenantId): Task[Option[Tenant]] = Task(tenantDatastore.get(tenantId))

  override def getTenantByName(tenantName: TenantName): Task[Option[Tenant]] =
    Task {
      tenantDatastore.collectFirst {
        case (_, tenant) if tenant.tenantName == tenantName => tenant
      }
    }

  override def getTenantByGroupId(groupId: TenantGroupId): Task[Option[Tenant]] =
    Task {
      tenantDatastore.collectFirst {
        case (_, tenant) if tenant.groupId == groupId => tenant
      }
    }

  override def deleteTenantById(id: TenantId): Task[Unit] =
    Task {
      tenantDatastore.remove(id)
    }
}
