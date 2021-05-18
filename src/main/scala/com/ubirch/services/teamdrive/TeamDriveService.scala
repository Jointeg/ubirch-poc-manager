package com.ubirch.services.teamdrive

import com.ubirch.models.auth.Base16String
import com.ubirch.models.auth.cert.Passphrase
import com.ubirch.services.teamdrive.TeamDriveService.SharedCertificate
import com.ubirch.services.teamdrive.model.{ FileId, Read, SpaceId, TeamDriveClient }
import monix.eval.Task

import java.nio.ByteBuffer
import javax.inject.{ Inject, Singleton }

trait TeamDriveService {
  def shareCert(
    spaceName: String,
    emails: Seq[String],
    passphrase: Passphrase,
    certificate: Base16String
  ): Task[SharedCertificate]
}

@Singleton
class TeamDriveServiceImpl @Inject() (client: TeamDriveClient) extends TeamDriveService {
  def shareCert(
    spaceName: String,
    emails: Seq[String],
    passphrase: Passphrase,
    certificate: Base16String
  ): Task[SharedCertificate] =
    for {
      spaceId <- client.createSpace(spaceName, spaceName)
      _ <- Task.sequence(emails.map(e => client.inviteMember(spaceId, e, Read)))
      certByteArray <- toByteArray(certificate)
      certFileId <- client.putFile(spaceId, "cert.pfx", ByteBuffer.wrap(certByteArray))
      passphraseFileId <- client.putFile(spaceId, "passphrase.txt", ByteBuffer.wrap(passphrase.value.getBytes))
    } yield SharedCertificate(
      spaceName = spaceName,
      spaceId = spaceId,
      passphraseFileId = passphraseFileId,
      certificateFileId = certFileId)

  private def toByteArray(base16String: Base16String): Task[Array[Byte]] = Task {
    // TODO copied from com.ubirch.models.auth.Base16String.toISO8859String
    base16String.value.sliding(2, 2).foldLeft(Array.empty[Byte])((acc, str) => {
      val byteValue = Integer.parseInt(str, 16)
      acc :+ byteValue.toByte
    })
  }
}

object TeamDriveService {
  case class SharedCertificate(spaceName: String, spaceId: SpaceId, passphraseFileId: FileId, certificateFileId: FileId)
}
