package com.ubirch.services.auth

import com.ubirch.UnitTestBase
import com.ubirch.models.auth.DecryptedData

import scala.concurrent.duration.DurationInt

class AESEncryptionTest extends UnitTestBase {

  "AESEncryption" should {
    "do some encryption" in {
      withInjector { injector =>
        val aesEncryption = injector.get[AESEncryption]
        val data = "dataToBeEncrypted"

        await(aesEncryption.encrypt(data)(identity), 2.seconds) shouldNot be("dataToBeEncrypted")
      }
    }

    "be same value after encrypting data and then decrypting it" in {
      withInjector { injector =>
        val aesEncryption = injector.get[AESEncryption]
        val data = "dataToBeEncrypted"
        val result = for {
          encryptedData <- aesEncryption.encrypt(data)(identity)
          decryptedData <- aesEncryption.decrypt(encryptedData)(identity)
        } yield decryptedData

        await(result, 2.seconds) shouldBe DecryptedData("dataToBeEncrypted")
      }
    }

    "result in different encrypted representation due to different IV while encrypting same data twice" in {
      withInjector { injector =>
        val aesEncryption = injector.get[AESEncryption]
        val data = "dataToBeEncrypted"
        val encryptionResult = for {
          encryptedData1 <- aesEncryption.encrypt(data)(identity)
          encryptedData2 <- aesEncryption.encrypt(data)(identity)
        } yield (encryptedData1, encryptedData2)

        val (encryptedData1, encryptedData2) = await(encryptionResult, 2.seconds)
        encryptedData1 shouldNot be(encryptedData2)

        val decryptionResult = for {
          decryptedData1 <- aesEncryption.decrypt(encryptedData1)(identity)
          decryptedData2 <- aesEncryption.decrypt(encryptedData2)(identity)
        } yield (decryptedData1, decryptedData2)

        val (decryptedData1, decryptedData2) = await(decryptionResult, 2.seconds)
        decryptedData1 shouldBe DecryptedData("dataToBeEncrypted")
        decryptedData2 shouldBe DecryptedData("dataToBeEncrypted")
      }
    }
  }

}
