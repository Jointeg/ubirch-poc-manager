package com.ubirch.models.auth
import com.ubirch.models.poc.DeviceId
import com.ubirch.models.tenant.TenantName

import java.util.UUID

case class CertIdentifier private (value: String)

object CertIdentifier {

  def pocOrgUnitCert(pocName: String, pocId: UUID): CertIdentifier = {
    new CertIdentifier(s"${pocName.take(30)} ${pocId.toString.take(5)}")
  }

  def pocClientCert(pocName: String, randomId: UUID): CertIdentifier = {
    new CertIdentifier(s"${pocName.take(30)} ${randomId.toString.take(5)}")
  }

  def tenantOrgCert(tenantName: TenantName): CertIdentifier = new CertIdentifier(tenantName.value)

  def tenantOrgUnitCert =
    new CertIdentifier(s"default point of certificate")

  def tenantClientCert =
    new CertIdentifier(s"default")

  def thingCert(pocName: String, deviceId: DeviceId): CertIdentifier = {
    new CertIdentifier(s"${pocName.take(30)} ${deviceId.toString.take(5)}")
  }
}
