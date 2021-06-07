package com.ubirch.services.teamdrive

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
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
class TeamDriveServiceImpl @Inject() (client: TeamDriveClient, pocConfig: PocConfig)
  extends TeamDriveService
  with LazyLogging
  with PocAuditLogging {
  def shareCert(
    spaceName: SpaceName,
    emails: Seq[String],
    passphrase: Passphrase,
    certificate: Base16String
  ): Task[SharedCertificate] = {
    client.withLogin {
      for {
        spaceId <- createSpace(spaceName)
        fileName = s"ubirch-client-certificate.pfx"
        _ <- Task.sequence(emails.map(e => client.inviteMember(spaceId, e, pocConfig.certWelcomeMessage, Read)))
        certByteArray <- Task(Base16String.toByteArray(certificate))
        certFileId <- client.putFile(spaceId, fileName, ByteBuffer.wrap(certByteArray))
        passphraseFileId <-
          client.putFile(spaceId, s"passwort.txt", ByteBuffer.wrap(passphrase.value.getBytes))
      } yield {
        logAuditEventInfo(s"uploaded cert with $fileName to TeamDrive space $spaceName")
        SharedCertificate(
          spaceName = spaceName,
          spaceId = spaceId,
          passphraseFileId = passphraseFileId,
          certificateFileId = certFileId)
      }
    }
  }

  /**
    * This method creates a space with the space name.
    * If the space already exists, retrieves the spaceId with the space name.
    */
  private def createSpace(spaceName: SpaceName): Task[SpaceId] = {
    client.createSpace(spaceName, spaceName.v) // use space name as path
      .map { spaceId =>
        logAuditEventInfo(s"created space for shared cert $spaceName")
        spaceId
      }.onErrorHandleWith {
        case ex if ex.getMessage.contains("exists") =>
          logger.warn(s"$spaceName was already created.")
          client.getSpaceIdByName(spaceName).attempt.map {
            case Right(spaceIdOpt) =>
              spaceIdOpt.getOrElse(throw TeamDriveError(s"$spaceName was not found."))
            case Left(exception) =>
              logger.error(s"unexpected error occurred when retrieving space: $spaceName.", exception)
              throw exception
          }
        case ex =>
          logger.error(s"unexpected error occurred when creating space: $spaceName.", ex)
          Task.raiseError(ex)
      }
  }
}

object TeamDriveService {
  case class SharedCertificate(
    spaceName: SpaceName,
    spaceId: SpaceId,
    passphraseFileId: FileId,
    certificateFileId: FileId)
}
