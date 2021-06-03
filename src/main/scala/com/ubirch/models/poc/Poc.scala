package com.ubirch.models.poc

import com.ubirch.models.tenant.{ ClientCert, OrgUnitId, TenantId }
import com.ubirch.services.keycloak.{ CertifyBmgRealm, CertifyUbirchRealm, KeycloakRealm }
import com.ubirch.util.ServiceConstants.POC_GROUP_PREFIX
import org.joda.time.DateTime

import java.util.UUID

case class Poc(
  id: UUID,
  tenantId: TenantId,
  externalId: String,
  pocType: String,
  pocName: String,
  address: Address,
  phone: String,
  certifyApp: Boolean,
  logoUrl: Option[LogoURL],
  clientCertRequired: Boolean,
  orgUnitId: Option[OrgUnitId] = None,
  clientCert: Option[ClientCert] = None,
  extraConfig: Option[JsonConfig],
  manager: PocManager,
  roleName: String,
  deviceGroupId: Option[String] = None,
  pocTenantTypeGroupId: Option[String] = None,
  certifyGroupId: Option[String] = None,
  adminGroupId: Option[String] = None,
  employeeGroupId: Option[String] = None,
  deviceId: DeviceId,
  clientCertFolder: Option[String] = None,
  status: Status = Pending,
  sharedAuthCertId: Option[UUID] = None,
  lastUpdated: Updated = Updated(DateTime.now()), //updated automatically on storage in DB
  created: Created = Created(DateTime.now())
) {
  def getDeviceId: String = deviceId.value.value.toString

  def getRealm: KeycloakRealm = {
    if (pocName.contains("bmg")) CertifyBmgRealm
    else CertifyUbirchRealm
  }

}

object Poc {

  def apply(
    id: UUID,
    tenantId: TenantId,
    externalId: String,
    pocType: String,
    pocName: String,
    address: Address,
    phone: String,
    certifyApp: Boolean,
    logoUrl: Option[LogoURL],
    clientCertRequired: Boolean,
    extraConfig: Option[JsonConfig],
    manager: PocManager,
    status: Status
  ): Poc = {

    val roleName = POC_GROUP_PREFIX + pocName.take(10) + "_" + id
    Poc(
      id = id,
      tenantId = tenantId,
      externalId = externalId,
      pocType = pocType,
      pocName = pocName,
      address = address,
      phone = phone,
      certifyApp = certifyApp,
      logoUrl = logoUrl,
      clientCertRequired = clientCertRequired,
      extraConfig = extraConfig,
      manager = manager,
      roleName = roleName,
      deviceId = DeviceId(tenantId, externalId),
      status = status
    )
  }

}
