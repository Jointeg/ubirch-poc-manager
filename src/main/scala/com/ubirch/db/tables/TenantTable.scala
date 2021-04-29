package com.ubirch.db.tables
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.models.tenant.{Tenant, TenantGroupId, TenantId, TenantName}
import monix.eval.Task

import javax.inject.Inject

trait TenantRepository {
  def createTenant(tenant: Tenant): Task[TenantId]

  def getTenant(tenantId: TenantId): Task[Option[Tenant]]

  def getTenantByName(tenantName: TenantName): Task[Option[Tenant]]

  def getTenantByGroupId(groupId: TenantGroupId): Task[Option[Tenant]]

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

  private def getTenantByGroupIdQuery(groupId: TenantGroupId) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").filter(_.groupId == lift(groupId))
    }

  private def deleteTenantByIdQuery(id: TenantId) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").filter(_.id == lift(id)).delete
    }


  override def createTenant(tenant: Tenant): Task[TenantId] = Task(run(createTenantQuery(tenant))).map(_ => tenant.id)

  override def getTenant(tenantId: TenantId): Task[Option[Tenant]] =
    Task(run(getTenantQuery(tenantId))).map(_.headOption)

  override def getTenantByName(tenantName: TenantName): Task[Option[Tenant]] =
    Task(run(getTenantByNameQuery(tenantName))).map(_.headOption)

  def getTenantByGroupId(groupId: TenantGroupId): Task[Option[Tenant]] = Task(run(getTenantByGroupIdQuery(groupId))).map(_.headOption)

  def deleteTenantById(id: TenantId): Task[Unit] = Task(run(deleteTenantByIdQuery(id)))
}
