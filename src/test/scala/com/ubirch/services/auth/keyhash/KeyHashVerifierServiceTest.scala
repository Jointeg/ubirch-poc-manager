package com.ubirch.services.auth.keyhash
import com.ubirch.db.tables.KeyHashRepository
import com.ubirch.e2e.{E2EInjectorHelperImpl, E2ETestBase}
import com.ubirch.models.auth.{Base64String, HashedData}
import com.ubirch.services.auth.HashingService
import com.ubirch.services.keyhash.KeyHashVerifierService

import scala.concurrent.duration.DurationInt

class KeyHashVerifierServiceTest extends E2ETestBase {

  "KeyHashVerifierService" should {
    "create a hash if one does not exists in key_hash table" in {
      withInjector { injector =>
        val keyHashVerifier = injector.get[KeyHashVerifierService]
        val keyHashRepository = injector.get[KeyHashRepository]

        val initialKeyHashFromDB = await(keyHashRepository.getFirst, 5.seconds)
        initialKeyHashFromDB shouldNot be(defined)

        val maybeKeyHash = for {
          _ <- keyHashVerifier.verifyHash(Base64String("NHQ3dzl6JEMmRilKQE5jUmZValhuMnI1dTh4L0ElRCo="))
          maybeKeyHash <- keyHashRepository.getFirst
        } yield maybeKeyHash

        val maybeKeyHashFromDB = await(maybeKeyHash, 5.seconds)
        maybeKeyHashFromDB shouldBe defined
      }
    }

    "successfully verify hash if such one already exists in key_hash table" in {
      withInjector { injector =>
        val keyHashVerifier = injector.get[KeyHashVerifierService]

        createInitialHashKey(injector)

        val verifyResult = keyHashVerifier.verifyHash(Base64String("NHQ3dzl6JEMmRilKQE5jUmZValhuMnI1dTh4L0ElRCo="))

        // If verification fails then exception is thrown
        await(verifyResult, 5.seconds)
      }
    }

    "fail to verify hash if it does not match the stored one" in {
      withInjector { injector =>
        val keyHashVerifier = injector.get[KeyHashVerifierService]

        createInitialHashKey(injector)

        val verifyResult = keyHashVerifier.verifyHash(Base64String.toBase64String("Not a hashed key"))

        assertThrows[RuntimeException](await(verifyResult, 5.seconds))
      }
    }
  }

  private def createInitialHashKey(injector: E2EInjectorHelperImpl): HashedData = {
    val keyHashRepository = injector.get[KeyHashRepository]
    val hashingService = injector.get[HashingService]

    val hashedData = hashingService.sha256(Base64String("NHQ3dzl6JEMmRilKQE5jUmZValhuMnI1dTh4L0ElRCo="))
    await(keyHashRepository.insertNewKeyHash(hashedData), 5.seconds)
    hashedData
  }

}
