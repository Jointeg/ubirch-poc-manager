package com.ubirch.services.teamdrive

import com.ubirch.models.auth.Base16String
import com.ubirch.models.auth.cert.Passphrase
import com.ubirch.services.teamdrive.TeamDriveService.SharedCertificate
import com.ubirch.services.teamdrive.model.{FileId, LoginInformation, Read, SpaceId, SpaceName, TeamDriveClient}
import monix.eval.Task

import java.nio.ByteBuffer
import javax.inject.{Inject, Singleton}

trait TeamDriveService {
  def shareCert(
    spaceName: SpaceName,
    emails: Seq[String],
    passphrase: Passphrase,
    certificate: Base16String
  ): Task[SharedCertificate]
}

@Singleton
class TeamDriveServiceImpl @Inject() (client: TeamDriveClient) extends TeamDriveService {
  def shareCert(
    spaceName: SpaceName,
    emails: Seq[String],
    passphrase: Passphrase,
    certificate: Base16String
  ): Task[SharedCertificate] =
    for {
      loginInformation <- client.getLoginInformation()
      _ <- loginInformation match {
        case LoginInformation(isLoginRequired) if isLoginRequired => client.login()
        case _ => Task.unit
      }
      spaceId <- client.createSpace(spaceName, spaceName.v) // use space name as path
      _ <- Task.sequence(emails.map(e => client.inviteMember(spaceId, e, Read)))
      certByteArray <- Task(Base16String.toByteArray(certificate))
      certFileId <- client.putFile(spaceId, s"cert_$spaceName.pfx", ByteBuffer.wrap(certByteArray))
      passphraseFileId <-
        client.putFile(spaceId, s"passphrase_$spaceName.pwd", ByteBuffer.wrap(passphrase.value.getBytes))
    } yield SharedCertificate(
      spaceName = spaceName,
      spaceId = spaceId,
      passphraseFileId = passphraseFileId,
      certificateFileId = certFileId)

}

object TeamDriveService {
  case class SharedCertificate(
    spaceName: SpaceName,
    spaceId: SpaceId,
    passphraseFileId: FileId,
    certificateFileId: FileId)
}
