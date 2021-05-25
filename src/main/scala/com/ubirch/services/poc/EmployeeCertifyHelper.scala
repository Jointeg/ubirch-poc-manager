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
import com.ubirch.services.poc.PocEmployeeCreator.throwError
import monix.eval.Task

import javax.inject.Inject

/**
  * This trait is for keycloak certify realm related employee creation operations
  */
trait EmployeeCertifyHelper {
  def createCertifyUserWithRequiredActions(pocEmployeeAndStatus: EmployeeAndStatus): Task[EmployeeAndStatus]

  def sendEmailToCertifyUser(pocEmployeeAndStatus: EmployeeAndStatus): Task[EmployeeAndStatus]

  def addGroupsToCertifyUser(pocEmployeeAndStatus: EmployeeAndStatus, poc: Poc): Task[EmployeeAndStatus]
}

class EmployeeCertifyHelperImpl @Inject() (users: KeycloakUserService) extends EmployeeCertifyHelper with LazyLogging {

  val requiredActions =
    List(UserRequiredAction.VERIFY_EMAIL, UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)

  @throws[PocEmployeeCreationError]
  override def createCertifyUserWithRequiredActions(pocEmployeeAndStatus: EmployeeAndStatus)
    : Task[EmployeeAndStatus] = {

    if (pocEmployeeAndStatus.status.certifyUserCreated) Task(pocEmployeeAndStatus)
    else {
      users.createUserWithoutUserName(getKeycloakUser(pocEmployeeAndStatus), CertifyKeycloak, requiredActions).map {
        case Right(userId) =>
          pocEmployeeAndStatus.copy(
            employee = pocEmployeeAndStatus.employee.copy(certifyUserId = Some(userId.value)),
            status = pocEmployeeAndStatus.status.copy(certifyUserCreated = true))
        case Left(UserAlreadyExists(userName)) =>
          logger.warn(s"user is already exist. $userName")
          pocEmployeeAndStatus.copy(status = pocEmployeeAndStatus.status.copy(certifyUserCreated = true))
        case Left(UserCreationError(errorMsg)) => PocEmployeeCreator.throwError(pocEmployeeAndStatus, errorMsg)
      }
    }
  }

  @throws[PocEmployeeCreationError]
  override def sendEmailToCertifyUser(eas: EmployeeAndStatus): Task[EmployeeAndStatus] = {
    if (eas.status.keycloakEmailSent) Task(eas)
    else if (eas.employee.certifyUserId.isEmpty)
      throwError(eas, s"certifyUser is missing, when it should be added to poc employee ${eas.employee.id}")
    else {
      users.sendRequiredActionsEmail(UserId(eas.employee.certifyUserId.get), CertifyKeycloak).map {
        case Right(_)       => eas.copy(status = eas.status.copy(keycloakEmailSent = true))
        case Left(errorMsg) => PocEmployeeCreator.throwError(eas, errorMsg)
      }
    }
  }

  override def addGroupsToCertifyUser(eAs: EmployeeAndStatus, poc: Poc): Task[EmployeeAndStatus] = {

    val userId = eAs.employee.certifyUserId
      .getOrElse(throwError(eAs, s"certifyUserId for ${eAs.employee.id} is missing, when groups should be added"))

    val employeeGroupId =
      poc.employeeGroupId.getOrElse(throwError(eAs, s"employeeGroupId is missing in poc ${poc.id}"))

    users.addGroupToUserById(UserId(userId), employeeGroupId, CertifyKeycloak).map {
      case Right(_)       => eAs.copy(status = eAs.status.copy(employeeGroupAssigned = true))
      case Left(errorMsg) => throwError(eAs, errorMsg)
    }
  }

  private def getKeycloakUser(eAs: EmployeeAndStatus): CreateKeycloakUserWithoutUserName = {
    CreateKeycloakUserWithoutUserName(
      FirstName(eAs.employee.name),
      LastName(eAs.employee.surname),
      Email(eAs.employee.email)
    )
  }

}
