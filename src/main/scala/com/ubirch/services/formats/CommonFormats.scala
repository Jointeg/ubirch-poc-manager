package com.ubirch.services.formats
import com.ubirch.models.NamespacedUUID
import com.ubirch.models.auth.Base64String
import com.ubirch.models.tenant.{ ClientCert, UsageType }
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
  private val clientCertFormat: CustomSerializer[ClientCert] =
    createStringFormat(string => ClientCert(Base64String(string)), string => Base64.isBase64(string))(_.value.value)

  val all = List(tenantIdFormat, usageTypeFormat, clientCertFormat)
}
