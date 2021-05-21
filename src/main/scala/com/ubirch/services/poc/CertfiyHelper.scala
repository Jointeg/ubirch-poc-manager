package com.ubirch.services.poc

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
import com.ubirch.models.keycloak.user.{
  CreateKeycloakUserWithoutUserName,
  UserAlreadyExists,
  UserCreationError,
  UserRequiredAction
}
import com.ubirch.models.poc.Poc
import com.ubirch.models.tenant.Tenant
import com.ubirch.models.user.{ Email, FirstName, LastName, UserId }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.PocAdminCreator.throwError
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.eval.Task

import java.util.UUID
import javax.inject.Inject

/**
  * This trait is for certify realm on keycloak related operations
  */
trait CertifyHelper {
  def createCertifyUserWithRequiredActions(pocAdminAndStatus: PocAdminAndStatus): Task[PocAdminAndStatus]

  def sendEmailToCertifyUser(pocAdminAndStatus: PocAdminAndStatus): Task[PocAdminAndStatus]

  def addGroupsToCertifyUser(pocAdminAndStatus: PocAdminAndStatus, poc: Poc, tenant: Tenant): Task[PocAdminAndStatus]
}

class CertifyHelperImpl @Inject() (users: KeycloakUserService, pocConfig: PocConfig)
  extends CertifyHelper
  with LazyLogging {

  @throws[PocAdminCreationError]
  override def createCertifyUserWithRequiredActions(pocAdminAndStatus: PocAdminAndStatus): Task[PocAdminAndStatus] = {
    if (pocAdminAndStatus.status.certifyUserCreated) Task(pocAdminAndStatus)
    else {
      val keycloakUser = CreateKeycloakUserWithoutUserName(
        FirstName(pocAdminAndStatus.admin.name),
        LastName(pocAdminAndStatus.admin.surname),
        Email(pocAdminAndStatus.admin.email)
      )
      val requiredActions =
        List(UserRequiredAction.VERIFY_EMAIL, UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)
      users.createUserWithoutUserName(keycloakUser, CertifyKeycloak, requiredActions).map {
        case Right(userId) =>
          pocAdminAndStatus.copy(
            admin = pocAdminAndStatus.admin.copy(certifyUserId = Some(userId.value)),
            status = pocAdminAndStatus.status.copy(certifyUserCreated = true))
        case Left(UserAlreadyExists(userName)) =>
          logger.warn(s"user is already exist. $userName")
          pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(certifyUserCreated = true))
        case Left(UserCreationError(errorMsg)) => PocAdminCreator.throwError(pocAdminAndStatus, errorMsg)
      }
    }
  }

  @throws[PocAdminCreationError]
  override def sendEmailToCertifyUser(pocAdminAndStatus: PocAdminAndStatus): Task[PocAdminAndStatus] = {
    if (pocAdminAndStatus.status.keycloakEmailSent) Task(pocAdminAndStatus)
    else if (pocAdminAndStatus.admin.certifyUserId.isEmpty) {
      throwError(
        pocAdminAndStatus,
        "certifyUserCreated is missing, when it should be added to poc admin")
    } else {
      users.sendRequiredActionsEmail(UserId(pocAdminAndStatus.admin.certifyUserId.get), CertifyKeycloak).map {
        case Right(_)       => pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(keycloakEmailSent = true))
        case Left(errorMsg) => PocAdminCreator.throwError(pocAdminAndStatus, errorMsg)
      }
    }
  }

  /**
    * Add poc.tenantGroupId and poc.CertifyGroupId, PocAdminGroupId into the user
    * @param pocAdminAndStatus
    * @return
    */
  override def addGroupsToCertifyUser(
    pocAdminAndStatus: PocAdminAndStatus,
    poc: Poc,
    tenant: Tenant): Task[PocAdminAndStatus] = {
    val userId = pocAdminAndStatus.admin.certifyUserId.getOrElse(
      throwError(
        pocAdminAndStatus,
        "certifyUserId is missing, when it should be added to certify")
    )
    for {
      status1 <- addGroupByIdToCertify(userId, pocConfig.pocAdminGroupId).map {
        case Right(_) =>
          pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(pocAdminGroupAssigned = true))
        case Left(errorMsg) =>
          throwError(pocAdminAndStatus, errorMsg)
      }
      certifyGroupId = poc.certifyGroupId.getOrElse(throwError(
        status1,
        "pocCertifyGroupId is missing, when it should be added to certify"))
      status2 <- addGroupByIdToCertify(userId, certifyGroupId).map {
        case Right(_) =>
          status1.copy(status = status1.status.copy(pocCertifyGroupAssigned = true))
        case Left(errorMsg) =>
          throwError(status1, errorMsg)
      }
      tenantGroupId = TENANT_GROUP_PREFIX + tenant.tenantName.value
      statusFinal <- addGroupByIdToCertify(userId, tenantGroupId).map {
        case Right(_) =>
          status2.copy(status = status2.status.copy(pocTenantGroupAssigned = true))
        case Left(errorMsg) =>
          throwError(status2, errorMsg)
      }
    } yield statusFinal
  }

  private def addGroupByIdToCertify(
    userId: UUID,
    groupId: String): Task[Either[String, Unit]] = {
    users.addGroupToUserByUserId(UserId(userId), groupId, CertifyKeycloak)
  }
}
