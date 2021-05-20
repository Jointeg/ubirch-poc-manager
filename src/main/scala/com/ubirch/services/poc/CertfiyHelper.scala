package com.ubirch.services.poc

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.PocConfig
import com.ubirch.models.keycloak.user.{ CreateKeycloakUser, UserAlreadyExists, UserCreationError, UserRequiredAction }
import com.ubirch.models.poc.Poc
import com.ubirch.models.tenant.Tenant
import com.ubirch.models.user.{ Email, FirstName, LastName, UserName }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.PocAdminCreator.throwError
import com.ubirch.util.ServiceConstants.TENANT_GROUP_PREFIX
import monix.eval.Task

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
    if (pocAdminAndStatus.status.certifierUserCreated) Task(pocAdminAndStatus)
    else {
      val keycloakUser = CreateKeycloakUser(
        FirstName(""),
        LastName(""),
        UserName(pocAdminAndStatus.admin.id.toString),
        Email(pocAdminAndStatus.admin.email)
      )
      val requiredActions =
        List(UserRequiredAction.VERIFY_EMAIL, UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)
      users.createUser(keycloakUser, CertifyKeycloak, requiredActions).map {
        case Right(_) => pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(certifierUserCreated = true))
        case Left(UserAlreadyExists(userName)) =>
          logger.warn(s"user is already exist. $userName")
          pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(certifierUserCreated = true))
        case Left(UserCreationError(errorMsg)) => PocAdminCreator.throwError(pocAdminAndStatus, errorMsg)
      }
    }
  }

  @throws[PocAdminCreationError]
  override def sendEmailToCertifyUser(pocAdminAndStatus: PocAdminAndStatus): Task[PocAdminAndStatus] = {
    if (pocAdminAndStatus.status.keycloakEmailSent) Task(pocAdminAndStatus)
    else {
      users.sendRequiredActionsEmail(UserName(pocAdminAndStatus.admin.id.toString), CertifyKeycloak).map {
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
    for {
      status1 <- addGroupByIdToCertify(pocConfig.pocAdminGroupId, pocAdminAndStatus).map {
        case Right(_) =>
          pocAdminAndStatus.copy(status = pocAdminAndStatus.status.copy(pocAdminGroupAssigned = true))
        case Left(errorMsg) =>
          throwError(pocAdminAndStatus, errorMsg)
      }
      certifyGroupId = poc.certifyGroupId.getOrElse(throwError(
        status1,
        "pocCertifyGroupId is missing, when it should be added to certify"))
      status2 <- addGroupByIdToCertify(certifyGroupId, status1).map {
        case Right(_) =>
          status1.copy(status = status1.status.copy(pocCertifyGroupAssigned = true))
        case Left(errorMsg) =>
          throwError(status1, errorMsg)
      }
      tenantGroupId = TENANT_GROUP_PREFIX + tenant.tenantName.value
      statusFinal <- addGroupByIdToCertify(tenantGroupId, status2).map {
        case Right(_) =>
          status2.copy(status = status2.status.copy(pocTenantGroupAssigned = true))
        case Left(errorMsg) =>
          throwError(status2, errorMsg)
      }
    } yield statusFinal
  }

  private def addGroupByIdToCertify(
    groupId: String,
    pocAdminAndStatus: PocAdminAndStatus): Task[Either[String, Unit]] = {
    val userId = pocAdminAndStatus.admin.id.toString
    users.addGroupToUser(userId, groupId, CertifyKeycloak)
  }
}
