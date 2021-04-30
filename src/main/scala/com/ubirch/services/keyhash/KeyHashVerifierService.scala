package com.ubirch.services.keyhash

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.db.tables.KeyHashRepository
import com.ubirch.models.auth.{Base64String, HashedData}
import com.ubirch.services.auth.HashingService
import monix.eval.Task

import javax.inject.Inject

trait KeyHashVerifierService {
  def verifyHash(base64String: Base64String): Task[Unit]
}

class DefaultKeyHashVerifier @Inject() (hashingService: HashingService, keyHashRepository: KeyHashRepository)
  extends KeyHashVerifierService
  with LazyLogging {
  override def verifyHash(key: Base64String): Task[Unit] = {
    keyHashRepository.getFirst
      .flatMap {
        case Some(hash) => Task(existingHashFlow(key, hash, hashingService))
        case None => insertNewHashFlow(key, hashingService, keyHashRepository)
      }
      .flatMap {
        case NewHashCreated => Task(logger.warn("Successfully created a new hash from provided key"))
        case HashDoesNotMatch =>
          logger.error("Provided key hash does not match with hash of already known hash")
          Task.raiseError(new RuntimeException("Provided key hash does not match with already known hash"))
        case HashMatches => Task(logger.info("Provided key hash matches with the stored one"))
      }
  }

  private def existingHashFlow(key: Base64String, existingHash: HashedData, hashingService: HashingService) = {
    val hashedIncomingKey = hashingService.sha256(key, existingHash.salt)
    if (hashedIncomingKey == existingHash) HashMatches else HashDoesNotMatch
  }

  private def insertNewHashFlow(
    key: Base64String,
    hashingService: HashingService,
    keyHashRepository: KeyHashRepository) = {
    for {
      _ <- Task(logger.info("Could not find any hash of key in key_hash table. Going to create one with provided key"))
      hashedKey = hashingService.sha256(key)
      _ <- keyHashRepository.insertNewKeyHash(hashedKey)
    } yield NewHashCreated
  }
}

sealed trait HashVerificationResult
case object NewHashCreated extends HashVerificationResult
case object HashDoesNotMatch extends HashVerificationResult
case object HashMatches extends HashVerificationResult
