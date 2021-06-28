package com.ubirch.services.auth

import com.ubirch.UnitTestBase
import com.ubirch.models.auth.Base64String

class HashingServiceTest extends UnitTestBase {

  "HashingService" should {
    "generate a salt and hash data" in {
      withInjector { injector =>
        val hashingService = injector.get[HashingService]
        val data = "Some data"
        val hashedData = hashingService.sha256(Base64String.toBase64String(data))
        hashedData.hash.value shouldNot be(Base64String.toBase64String(data))
      }
    }

    "result in two different results when hashing same data due to different salts" in {
      withInjector { injector =>
        val hashingService = injector.get[HashingService]
        val data = "Some data"
        val hashedData1 = hashingService.sha256(Base64String.toBase64String(data))
        val hashedData2 = hashingService.sha256(Base64String.toBase64String(data))

        hashedData1.hash shouldNot be(hashedData2.hash)
        hashedData1.salt.value.toList shouldNot be(hashedData2.salt.value.toList)
      }
    }

    "be able to hash to same value by using same salt" in {
      withInjector { injector =>
        val hashingService = injector.get[HashingService]
        val data = "Some data"
        val hashedData1 = hashingService.sha256(Base64String.toBase64String(data))
        val hashedData2 = hashingService.sha256(Base64String.toBase64String(data), hashedData1.salt)

        hashedData1.hash shouldBe hashedData2.hash
        hashedData1.salt.value.toList shouldBe hashedData2.salt.value.toList
      }
    }
  }

}
