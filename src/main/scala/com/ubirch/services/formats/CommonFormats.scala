package com.ubirch.services.formats
import com.ubirch.models.NamespacedUUID
import com.ubirch.models.auth.cert.Passphrase
import com.ubirch.models.auth.{ Base16String, Base64String }
import com.ubirch.models.tenant.{ ClientCert, TenantType, UsageType }
import org.apache.commons.codec.binary.Base64
import org.json4s.CustomSerializer

import java.util.UUID

object CommonFormats extends FormatHelperMethods {
  private val tenantIdFormat: CustomSerializer[NamespacedUUID] =
    createStringFormat(
      string => NamespacedUUID.fromJavaUUID(UUID.fromString(string)),
      _ => true)(
      _.value.toString)

  private val usageTypeFormat: CustomSerializer[UsageType] =
    createStringFormat(UsageType.unsafeFromString, _.nonEmpty)(UsageType.toStringFormat)
  private val tenantTypeFormat: CustomSerializer[TenantType] =
    createStringFormat(TenantType.unsafeFromString, _.nonEmpty)(TenantType.toStringFormat)
  private val clientCertFormat: CustomSerializer[ClientCert] =
    createStringFormat(string => ClientCert(Base64String(string)), string => Base64.isBase64(string))(_.value.value)
  private val base16String: CustomSerializer[Base16String] =
    createStringFormat(string => Base16String(string), _ => true)(_.value)
  private val passphraseFormat: CustomSerializer[Passphrase] =
    createStringFormat(Passphrase.apply, _ => true)(_.value)

  val all = List(tenantIdFormat, usageTypeFormat, tenantTypeFormat, clientCertFormat, base16String, passphraseFormat)
}
