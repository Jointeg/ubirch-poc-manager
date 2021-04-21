package com.ubirch.db.tables
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.models.tenant.Tenant
import monix.eval.Task

import javax.inject.Inject

trait TenantRepository {
  def createTenant(tenant: Tenant): Task[Unit]
}

class TenantTable @Inject() (quillJdbcContext: QuillJdbcContext) extends TenantRepository {
  import quillJdbcContext.ctx._

  private def createTenantQuery(tenant: Tenant) =
    quote {
      querySchema[Tenant]("poc_manager.tenants").insert(lift(tenant))
    }

  override def createTenant(tenant: Tenant): Task[Unit] = Task(run(createTenantQuery(tenant)))
}
