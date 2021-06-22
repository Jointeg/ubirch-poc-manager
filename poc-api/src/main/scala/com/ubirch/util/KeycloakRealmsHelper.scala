package com.ubirch.util
import com.ubirch.models.poc.Poc
import com.ubirch.models.tenant.{ BMG, Tenant, TenantType, UBIRCH }
import com.ubirch.services.keycloak.{ CertifyBmgRealm, CertifyUbirchRealm, KeycloakRealm }

object KeycloakRealmsHelper {

  implicit class TenantHelper(tenant: Tenant) {
    def getRealm: KeycloakRealm = tenant.tenantType match {
      case UBIRCH => CertifyUbirchRealm
      case BMG    => CertifyBmgRealm
    }
  }

  implicit class PocHelper(poc: Poc) {
    def getRealm: KeycloakRealm = {
      if (poc.pocType.contains("bmg")) CertifyBmgRealm
      else CertifyUbirchRealm
    }
  }

  implicit class TenantTypeHelper(tenantType: TenantType) {
    def getRealm: KeycloakRealm =
      tenantType match {
        case UBIRCH => CertifyUbirchRealm
        case BMG    => CertifyBmgRealm
      }
  }

}
