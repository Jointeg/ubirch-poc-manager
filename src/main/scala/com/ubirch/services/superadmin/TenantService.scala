package com.ubirch.services.superadmin
import com.ubirch.models.tenant.CreateTenantRequest
import monix.eval.Task

trait TenantService {

  def createTenant(createTenantRequest: CreateTenantRequest): Task[Unit]

}
