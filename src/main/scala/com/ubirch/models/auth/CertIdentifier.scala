package com.ubirch.models.auth
import com.ubirch.models.poc.DeviceId
import com.ubirch.models.tenant.TenantName

import java.util.UUID

case class CertIdentifier private (value: String)

object CertIdentifier {

  def pocOrgUnitCert(tenantName: TenantName, pocName: String, pocId: UUID): CertIdentifier = {
    new CertIdentifier(s"${tenantName.value} $pocName ${pocId.toString.take(5)}")
  }

  def pocClientCert(tenantName: TenantName, pocName: String, randomId: UUID): CertIdentifier = {
    new CertIdentifier(s"${tenantName.value} $pocName ${randomId.toString.take(5)}")
  }

  def orgCert(tenantName: TenantName): CertIdentifier = new CertIdentifier(tenantName.value)

  def tenantOrgUnitCert(tenantName: TenantName) = new CertIdentifier(s"${tenantName.value} default")

  def tenantClientCert(tenantName: TenantName) = new CertIdentifier(s"${tenantName.value} default point of certificate")

  def thingCert(tenantName: TenantName, pocName: String, deviceId: DeviceId): CertIdentifier = {
    new CertIdentifier(s"${tenantName.value} $pocName ${deviceId.toString.take(5)}")
  }
}
