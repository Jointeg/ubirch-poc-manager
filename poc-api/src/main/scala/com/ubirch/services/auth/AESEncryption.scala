package com.ubirch.services.auth

import com.ubirch.models.auth.{ DecryptedData, EncryptedData }
import monix.eval.Task
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.{ SecureRandom, Security }
import javax.crypto.spec.IvParameterSpec
import javax.crypto.{ Cipher, SecretKey }
import javax.inject.{ Inject, Singleton }

trait AESEncryption {
  def encrypt[Result](dataToBeEncrypted: String)(mapper: EncryptedData => Result): Task[Result]
  def decrypt[Result](encryptedData: EncryptedData)(mapper: DecryptedData => Result): Task[Result]
}

@Singleton
class AESEncryptionCBCMode @Inject() (aesKeyProvider: AESKeyProvider) extends AESEncryption {
  Security.addProvider(new BouncyCastleProvider())

  private val IV_LENGTH = 16
  private val AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding"

  private type InitializationVector = Array[Byte]
  private type EncryptedDataBytes = Array[Byte]

  override def encrypt[Result](dataToBeEncrypted: String)(mapper: EncryptedData => Result): Task[Result] = {
    for {
      secretKey <- aesKeyProvider.getAESKey
      iv <- generateIV()
      cipher <- initCipher(Cipher.ENCRYPT_MODE, secretKey, iv)
      cipherText <- cipherData(cipher, dataToBeEncrypted.getBytes(StandardCharsets.UTF_8))
    } yield mapper(EncryptedData.fromIVAndDataBytes(iv.getIV, cipherText))
  }

  override def decrypt[Result](encryptedData: EncryptedData)(mapper: DecryptedData => Result): Task[Result] = {
    for {
      secretKey <- aesKeyProvider.getAESKey
      ivWithData <- splitEncryptedDataByIV(encryptedData)
      (iv, encData) = ivWithData
      cipher <- initCipher(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv))
      decryptedData <- cipherData(cipher, encData)
    } yield mapper(DecryptedData.fromByteArray(decryptedData))
  }

  private def generateIV(): Task[IvParameterSpec] =
    Task {
      val ivBytes = new Array[Byte](IV_LENGTH)
      new SecureRandom().nextBytes(ivBytes)
      new IvParameterSpec(ivBytes)
    }

  private def splitEncryptedDataByIV(encryptedData: EncryptedData): Task[(InitializationVector, EncryptedDataBytes)] =
    Task {
      val encryptedDataWithIV = ByteBuffer.wrap(encryptedData.value.decode)

      val iv = new Array[Byte](IV_LENGTH)
      encryptedDataWithIV.get(iv)

      val encryptedDataBytes = new Array[Byte](encryptedDataWithIV.remaining())
      encryptedDataWithIV.get(encryptedDataBytes)

      (iv, encryptedDataBytes)
    }

  private def initCipher(mode: Int, secretKey: SecretKey, iv: IvParameterSpec) =
    Task {
      val cipher = Cipher.getInstance(AES_CBC_PKCS5, "BC")
      cipher.init(mode, secretKey, iv)
      cipher
    }

  private def cipherData(cipher: Cipher, dataToBeEncrypted: Array[Byte]) =
    Task(cipher.doFinal(dataToBeEncrypted))
}
