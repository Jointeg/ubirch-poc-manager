package com.ubirch.services.auth
import com.ubirch.models.auth.{DecryptedData, EncryptedData}
import monix.eval.Task

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.spec.IvParameterSpec
import javax.crypto.{Cipher, SecretKey}
import javax.inject.Inject

trait AESEncryption {
  def encrypt(dataToBeEncrypted: String): Task[EncryptedData]
  def decrypt(encryptedData: EncryptedData): Task[DecryptedData]
}

class AESEncryptionCBCMode @Inject() (aesKeyProvider: AESKeyProvider) extends AESEncryption {
  private val IV_LENGTH = 16
  private val AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding"

  private type InitializationVector = Array[Byte]
  private type EncryptedDataBytes = Array[Byte]

  override def encrypt(dataToBeEncrypted: String): Task[EncryptedData] = {
    for {
      secretKey <- aesKeyProvider.getAESKey
      iv <- generateIV()
      cipher <- initCipher(Cipher.ENCRYPT_MODE, secretKey, iv)
      cipherText <- cipherData(cipher, dataToBeEncrypted.getBytes(StandardCharsets.UTF_8))
    } yield EncryptedData.fromIVAndDataBytes(iv.getIV, cipherText)
  }
  override def decrypt(encryptedData: EncryptedData): Task[DecryptedData] = {
    for {
      secretKey <- aesKeyProvider.getAESKey
      ivWithData <- splitEncryptedDataByIV(encryptedData)
      (iv, encData) = ivWithData
      cipher <- initCipher(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv))
      decryptedData <- cipherData(cipher, encData)
    } yield DecryptedData.fromByteArray(decryptedData)
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
      val cipher = Cipher.getInstance(AES_CBC_PKCS5)
      cipher.init(mode, secretKey, iv)
      cipher
    }

  private def cipherData(cipher: Cipher, dataToBeEncrypted: Array[Byte]) =
    Task(cipher.doFinal(dataToBeEncrypted))
}
