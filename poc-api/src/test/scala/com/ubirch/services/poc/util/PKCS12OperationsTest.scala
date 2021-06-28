package com.ubirch.services.poc.util

import com.ubirch.UnitTestBase
import com.ubirch.models.auth.Base16String
import com.ubirch.models.auth.cert.Passphrase
import com.ubirch.services.poc.TestCertHandler

class PKCS12OperationsTest extends UnitTestBase {

  "PKCS12 Operations" should {
    "successfully create Keystore from hexadecimal string that represents PKCS12" in {
      val maybeKeystore =
        PKCS12Operations.recreateFromBase16String(TestCertHandler.validPkcs12, TestCertHandler.passphrase)
      maybeKeystore.right.value
    }

    "Fail to create Keystore if passphrase is invalid" in {
      val maybeKeystore =
        PKCS12Operations.recreateFromBase16String(TestCertHandler.validPkcs12, Passphrase("Invalid passphrase"))
      maybeKeystore shouldBe Left(PKCS12RecreationError)
    }

    "Fail to create Keystore if provided string does not represent PKCS12" in {
      val maybeKeystore =
        PKCS12Operations.recreateFromBase16String(Base16String("ABCDEF"), TestCertHandler.passphrase)
      maybeKeystore shouldBe Left(PKCS12RecreationError)
    }

    "Fail to create Keystore if provided string is not in hex format" in {
      val maybeKeystore =
        PKCS12Operations.recreateFromBase16String(Base16String("GHIJK"), TestCertHandler.passphrase)
      maybeKeystore shouldBe Left(PKCS12RecreationError)
    }
  }

}
