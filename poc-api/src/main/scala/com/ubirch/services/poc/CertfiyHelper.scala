package com.ubirch.services.poc

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.{
  CreateKeycloakUserWithoutUserName,
  UserAlreadyExists,
  UserCreationError,
  UserRequiredAction
}
import com.ubirch.models.poc.Poc
import com.ubirch.models.user.{ Email, FirstName, LastName, UserId }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.PocAdminCreator.throwError
import monix.eval.Task

import javax.inject.Inject

/**
  * This trait is for certify realm on keycloak related operations
  */
trait AdminCertifyHelper {
  def createCertifyUserWithRequiredActions(pocAdminAndStatus: PocAdminAndStatus): Task[PocAdminAndStatus]

  def sendEmailToCertifyUser(pocAdminAndStatus: PocAdminAndStatus): Task[PocAdminAndStatus]

  def addGroupsToCertifyUser(pocAdminAndStatus: PocAdminAndStatus, poc: Poc): Task[PocAdminAndStatus]
}

class AdminCertifyHelperImpl @Inject() (users: KeycloakUserService) extends AdminCertifyHelper with LazyLogging {

  val requiredActions =
    List(UserRequiredAction.VERIFY_EMAIL, UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)

  @throws[PocAdminCreationError]
  override def createCertifyUserWithRequiredActions(pocAdminAndStatus: PocAdminAndStatus): Task[PocAdminAndStatus] = {
    if (pocAdminAndStatus.status.certifyUserCreated) Task(pocAdminAndStatus)
    else {
      users.createUserWithoutUserName(
        CertifyKeycloak.defaultRealm,
        getKeycloakUser(pocAdminAndStatus),
        CertifyKeycloak,
        requiredActions).map {
        case Right(userId) =>
          pocAdminAndStatus.copy(
            admin = pocAdminAndStatus.admin.copy(certifyUserId = Some(userId.value)),
            status = pocAdminAndStatus.status.copy(certifyUserCreated = true))
        case Left(UserAlreadyExists(userName)) =>
          logger.error(s"user already exists. user: $userName, admin: ${pocAdminAndStatus.admin.id}")
          PocAdminCreator.throwError(
            pocAdminAndStatus,
            "This user already exists in Keycloak. Please contact Ubirch admin.")
        case Left(UserCreationError(errorMsg)) => PocAdminCreator.throwError(pocAdminAndStatus, errorMsg)
      }
    }
  }

  private def getKeycloakUser(pocAdminAndStatus: PocAdminAndStatus): CreateKeycloakUserWithoutUserName = {
    CreateKeycloakUserWithoutUserName(
      FirstName(pocAdminAndStatus.admin.name),
      LastName(pocAdminAndStatus.admin.surname),
      Email(pocAdminAndStatus.admin.email)
    )
  }

  @throws[PocAdminCreationError]
  override def sendEmailToCertifyUser(aAs: PocAdminAndStatus): Task[PocAdminAndStatus] = {
    if (aAs.status.keycloakEmailSent) Task(aAs)
    else if (aAs.admin.certifyUserId.isEmpty)
      throwError(aAs, s"certifyUserId is missing for ${aAs.admin.id}, when it should be added to poc admin group")
    else {
      users.sendRequiredActionsEmail(
        CertifyKeycloak.defaultRealm,
        UserId(aAs.admin.certifyUserId.get),
        CertifyKeycloak).map {
        case Right(_)       => aAs.copy(status = aAs.status.copy(keycloakEmailSent = true))
        case Left(errorMsg) => PocAdminCreator.throwError(aAs, errorMsg)
      }
    }
  }

  override def addGroupsToCertifyUser(pocAdminAndStatus: PocAdminAndStatus, poc: Poc): Task[PocAdminAndStatus] = {

    val userId = pocAdminAndStatus.admin.certifyUserId
      .getOrElse(throwError(
        pocAdminAndStatus,
        s"certifyUserId for ${pocAdminAndStatus.admin.id} is missing, when groups should be added"))
    val adminGroupId =
      poc.adminGroupId.getOrElse(throwError(pocAdminAndStatus, s"adminGroupId is missing in poc ${poc.id}"))

    users.addGroupToUserById(CertifyKeycloak.defaultRealm, UserId(userId), adminGroupId, CertifyKeycloak).map {
      case Right(_) =>
        pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(pocAdminGroupAssigned = true))
      case Left(errorMsg) =>
        throwError(pocAdminAndStatus, errorMsg)
    }
  }
}
