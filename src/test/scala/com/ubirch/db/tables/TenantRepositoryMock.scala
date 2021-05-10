package com.ubirch.db.tables
import com.ubirch.models.tenant.{ Tenant, TenantDeviceGroupId, TenantId, TenantName, TenantUserGroupId }
import monix.eval.Task

import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TenantRepositoryMock extends TenantRepository {
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

  def getTenantByDeviceGroupId(groupId: TenantDeviceGroupId): Task[Option[Tenant]] =
    Task {
      tenantDatastore.collectFirst {
        case (_, tenant) if tenant.deviceGroupId == groupId => tenant
      }
    }

  def getTenantByUserGroupId(groupId: TenantUserGroupId): Task[Option[Tenant]] =
    Task {
      tenantDatastore.collectFirst {
        case (_, tenant) if tenant.userGroupId == groupId => tenant
      }
    }

  def updateTenant(tenantUpdated: Tenant): Task[Unit] =
    Task {
      tenantDatastore.update(tenantUpdated.id, tenantUpdated)
    }

  override def deleteTenantById(id: TenantId): Task[Unit] =
    Task {
      tenantDatastore.remove(id)
    }
}
