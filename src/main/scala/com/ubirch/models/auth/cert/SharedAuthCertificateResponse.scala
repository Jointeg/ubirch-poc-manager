package com.ubirch.models.auth.cert
import com.ubirch.models.auth.Base16String

import java.util.UUID

case class SharedAuthCertificateResponse(certUuid: UUID, passphrase: Passphrase, pkcs12: Base16String)
case class Passphrase(value: String) extends AnyVal
