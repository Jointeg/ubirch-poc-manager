package com.ubirch.services.teamdrive

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.auth.Base16String
import com.ubirch.models.auth.cert.Passphrase
import com.ubirch.services.teamdrive.TeamDriveService.SharedCertificate
import com.ubirch.services.teamdrive.model._
import com.ubirch.util.PocAuditLogging
import monix.eval.Task

import java.nio.ByteBuffer
import javax.inject.{ Inject, Singleton }

trait TeamDriveService {
  def shareCert(
    spaceName: SpaceName,
    emails: Seq[String],
    passphrase: Passphrase,
    certificate: Base16String
  ): Task[SharedCertificate]
}

@Singleton
class TeamDriveServiceImpl @Inject() (client: TeamDriveClient)
  extends TeamDriveService
  with LazyLogging
  with PocAuditLogging {
  def shareCert(
    spaceName: SpaceName,
    emails: Seq[String],
    passphrase: Passphrase,
    certificate: Base16String
  ): Task[SharedCertificate] =
    for {
      loginInformation <- client.getLoginInformation()
      _ <- Task(logger.debug(s"TeamDrive agent requires login: ${loginInformation.isLoginRequired}"))
      _ <- loginInformation match {
        case LoginInformation(isLoginRequired) if isLoginRequired => client.login()
        case _                                                    => Task.unit
      }
      spaceId <- client.createSpace(spaceName, spaceName.v) // use space name as path
        .map { spaceId =>
          logAuditEventInfo(s"created space for shared cert $spaceName")
          spaceId
        }
      fileName = s"cert_$spaceName.pfx"
      _ <- Task.sequence(emails.map(e => client.inviteMember(spaceId, e, Read)))
      certByteArray <- Task(Base16String.toByteArray(certificate))
      certFileId <- client.putFile(spaceId, fileName, ByteBuffer.wrap(certByteArray))
      passphraseFileId <-
        client.putFile(spaceId, s"passphrase_$spaceName.pwd", ByteBuffer.wrap(passphrase.value.getBytes))
    } yield {
      logAuditEventInfo(s"uploaded cert with $fileName to TeamDrive space $spaceName")
      SharedCertificate(
        spaceName = spaceName,
        spaceId = spaceId,
        passphraseFileId = passphraseFileId,
        certificateFileId = certFileId)
    }

}

object TeamDriveService {
  case class SharedCertificate(
    spaceName: SpaceName,
    spaceId: SpaceId,
    passphraseFileId: FileId,
    certificateFileId: FileId)
}
