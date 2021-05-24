package com.ubirch.db.tables
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.tenant.{ Tenant, TenantCertifyGroupId, TenantDeviceGroupId, TenantId, TenantName }
import monix.eval.Task

import javax.inject.Inject

trait TenantRepository {
  def createTenant(tenant: Tenant): Task[TenantId]

  def getTenant(tenantId: TenantId): Task[Option[Tenant]]

  def getTenantByName(tenantName: TenantName): Task[Option[Tenant]]

  def getTenantByDeviceGroupId(groupId: TenantDeviceGroupId): Task[Option[Tenant]]

  def getTenantByCertifyGroupId(groupId: TenantCertifyGroupId): Task[Option[Tenant]]

  def updateTenant(tenant: Tenant): Task[Unit]

  def deleteTenantById(id: TenantId): Task[Unit]

}

class TenantTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends TenantRepository {
  import QuillMonixJdbcContext.ctx._

  private def createTenantQuery(tenant: Tenant) =
    quote {
      querySchema[Tenant]("poc_manager.tenant_table").insert(lift(tenant))
    }

  private def getTenantQuery(tenantId: TenantId) =
    quote {
      querySchema[Tenant]("poc_manager.tenant_table").filter(_.id == lift(tenantId))
    }

  private def getTenantByNameQuery(tenantName: TenantName) =
    quote {
      querySchema[Tenant]("poc_manager.tenant_table").filter(_.tenantName == lift(tenantName))
    }

  private def getTenantByDeviceGroupIdQuery(groupId: TenantDeviceGroupId) =
    quote {
      querySchema[Tenant]("poc_manager.tenant_table").filter(_.deviceGroupId == lift(groupId))
    }

  private def getTenantByUserGroupIdQuery(groupId: TenantCertifyGroupId) =
    quote {
      querySchema[Tenant]("poc_manager.tenant_table").filter(_.certifyGroupId == lift(groupId))
    }

  private def updateTenantQuery(tenant: Tenant) =
    quote {
      querySchema[Tenant]("poc_manager.tenant_table").filter(_.id == lift(tenant.id)).update(lift(tenant))
    }

  private def deleteTenantByIdQuery(id: TenantId) =
    quote {
      querySchema[Tenant]("poc_manager.tenant_table").filter(_.id == lift(id)).delete
    }

  def createTenant(tenant: Tenant): Task[TenantId] = run(createTenantQuery(tenant)).map(_ => tenant.id)

  def getTenant(tenantId: TenantId): Task[Option[Tenant]] =
    run(getTenantQuery(tenantId)).map(_.headOption)

  def getTenantByName(tenantName: TenantName): Task[Option[Tenant]] =
    run(getTenantByNameQuery(tenantName)).map(_.headOption)

  def getTenantByDeviceGroupId(groupId: TenantDeviceGroupId): Task[Option[Tenant]] =
    run(getTenantByDeviceGroupIdQuery(groupId)).map(_.headOption)

  def getTenantByCertifyGroupId(groupId: TenantCertifyGroupId): Task[Option[Tenant]] =
    run(getTenantByUserGroupIdQuery(groupId)).map(_.headOption)

  def updateTenant(tenant: Tenant): Task[Unit] = run(updateTenantQuery(tenant)).void

  def deleteTenantById(id: TenantId): Task[Unit] = run(deleteTenantByIdQuery(id)).void
}
