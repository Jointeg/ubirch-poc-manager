package com.ubirch.services.poc

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.user.{
  CreateKeycloakUserWithoutUserName,
  UserAlreadyExists,
  UserCreationError,
  UserRequiredAction
}
import com.ubirch.models.pocEmployee.PocEmployee
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
  def createCertifyUserWithRequiredActions(employeeTriple: EmployeeTriple): Task[EmployeeTriple]

  def sendEmailToCertifyUser(triple: EmployeeTriple): Task[EmployeeTriple]

  def addGroupsToCertifyUser(triple: EmployeeTriple): Task[EmployeeTriple]
}

class EmployeeCertifyHelperImpl @Inject() (users: KeycloakUserService) extends EmployeeCertifyHelper with LazyLogging {

  val requiredActions =
    List(UserRequiredAction.VERIFY_EMAIL, UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)

  @throws[PocEmployeeCreationError]
  override def createCertifyUserWithRequiredActions(triple: EmployeeTriple): Task[EmployeeTriple] = {

    if (triple.status.certifyUserCreated) Task(triple)
    else {
      users.createUserWithoutUserName(
        triple.poc.getRealm,
        getKeycloakUser(triple.employee),
        CertifyKeycloak,
        requiredActions).map {
        case Right(userId) =>
          triple.copy(
            employee = triple.employee.copy(certifyUserId = Some(userId.value)),
            status = triple.status.copy(certifyUserCreated = true))
        case Left(UserAlreadyExists(userName)) =>
          logger.error(s"user already exists. user: $userName, employee: ${triple.employee.id}")
          PocEmployeeCreator.throwError(
            triple,
            "This user already exists in Keycloak. Please contact Ubirch admin.")
        case Left(UserCreationError(errorMsg)) => PocEmployeeCreator.throwError(triple, errorMsg)
      }
    }
  }

  @throws[PocEmployeeCreationError]
  override def sendEmailToCertifyUser(triple: EmployeeTriple): Task[EmployeeTriple] = {
    if (triple.status.keycloakEmailSent) Task(triple)
    else if (triple.employee.certifyUserId.isEmpty)
      throwError(
        triple,
        s"certifyUserId is missing, when trying to send email invitation to poc employee ${triple.employee.id}")
    else {
      users.sendRequiredActionsEmail(
        triple.poc.getRealm,
        UserId(triple.employee.certifyUserId.get),
        CertifyKeycloak).map {
        case Right(_)       => triple.copy(status = triple.status.copy(keycloakEmailSent = true))
        case Left(errorMsg) => PocEmployeeCreator.throwError(triple, errorMsg)
      }
    }
  }

  override def addGroupsToCertifyUser(triple: EmployeeTriple): Task[EmployeeTriple] = {

    val userId = triple.employee.certifyUserId
      .getOrElse(throwError(triple, s"certifyUserId for ${triple.employee.id} is missing, when groups should be added"))

    val employeeGroupId =
      triple.poc.employeeGroupId.getOrElse(throwError(triple, s"employeeGroupId is missing in poc ${triple.poc.id}"))

    users.addGroupToUserById(triple.poc.getRealm, UserId(userId), employeeGroupId, CertifyKeycloak).map {
      case Right(_)       => triple.copy(status = triple.status.copy(employeeGroupAssigned = true))
      case Left(errorMsg) => throwError(triple, errorMsg)
    }
  }

  private def getKeycloakUser(employee: PocEmployee): CreateKeycloakUserWithoutUserName = {
    CreateKeycloakUserWithoutUserName(
      FirstName(employee.name),
      LastName(employee.surname),
      Email(employee.email)
    )
  }

}
