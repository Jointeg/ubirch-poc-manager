package com.ubirch.db.tables
import com.ubirch.db.context.QuillJdbcContext
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

class TenantTable @Inject() (quillJdbcContext: QuillJdbcContext) extends TenantRepository {
  import quillJdbcContext.ctx._

  private def createTenantQuery(tenant: Tenant) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").insert(lift(tenant))
    }

  private def getTenantQuery(tenantId: TenantId) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").filter(_.id == lift(tenantId))
    }

  private def getTenantByNameQuery(tenantName: TenantName) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").filter(_.tenantName == lift(tenantName))
    }

  private def getTenantByDeviceGroupIdQuery(groupId: TenantDeviceGroupId) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").filter(_.deviceGroupId == lift(groupId))
    }

  private def getTenantByUserGroupIdQuery(groupId: TenantCertifyGroupId) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").filter(_.certifyGroupId == lift(groupId))
    }

  private def updateTenantQuery(tenant: Tenant) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").filter(_.id == lift(tenant.id)).update(lift(tenant))
    }

  private def deleteTenantByIdQuery(id: TenantId) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").filter(_.id == lift(id)).delete
    }

  def createTenant(tenant: Tenant): Task[TenantId] = Task(run(createTenantQuery(tenant))).map(_ => tenant.id)

  def getTenant(tenantId: TenantId): Task[Option[Tenant]] =
    Task(run(getTenantQuery(tenantId))).map(_.headOption)

  def getTenantByName(tenantName: TenantName): Task[Option[Tenant]] =
    Task(run(getTenantByNameQuery(tenantName))).map(_.headOption)

  def getTenantByDeviceGroupId(groupId: TenantDeviceGroupId): Task[Option[Tenant]] =
    Task(run(getTenantByDeviceGroupIdQuery(groupId))).map(_.headOption)

  def getTenantByCertifyGroupId(groupId: TenantCertifyGroupId): Task[Option[Tenant]] =
    Task(run(getTenantByUserGroupIdQuery(groupId))).map(_.headOption)

  def updateTenant(tenant: Tenant): Task[Unit] = Task(run(updateTenantQuery(tenant)))

  def deleteTenantById(id: TenantId): Task[Unit] = Task(run(deleteTenantByIdQuery(id)))
}
