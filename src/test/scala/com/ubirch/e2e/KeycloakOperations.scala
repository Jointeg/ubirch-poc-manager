package com.ubirch.e2e

import cats.implicits._
import com.ubirch.models.user.{ UserId, UserName }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.keycloak.{ CertifyKeycloakConnector, DeviceKeycloakConnector }
import com.ubirch.{ Awaits, ExecutionContextsTests }
import monix.eval.Task
import org.keycloak.representations.idm.{ CredentialRepresentation, UserRepresentation }
import org.scalatest.Matchers.fail
import org.scalatest.{ EitherValues, OptionValues }

import scala.collection.JavaConverters.{
  iterableAsScalaIterableConverter,
  mapAsJavaMapConverter,
  seqAsJavaListConverter
}

trait KeycloakOperations extends ExecutionContextsTests with Awaits with OptionValues with EitherValues {

  val DEVICE_REALM = "device-realm"
  val CERTIFY_REALM = "certify-realm"

  def cleanAllUsers(
    keycloakUsersConnector: CertifyKeycloakConnector,
    keycloakDeviceConnector: DeviceKeycloakConnector): Task[Unit] = {
    for {
      allUsers <- Task(keycloakUsersConnector.keycloak.realm(CERTIFY_REALM).users().list().asScala.toList)
      _ <-
        allUsers.traverse_(user =>
          Task(keycloakUsersConnector.keycloak.realm(CERTIFY_REALM).users().delete(user.getId)))
      allDeviceUsers <- Task(keycloakDeviceConnector.keycloak.realm(DEVICE_REALM).users().list().asScala.toList)
      _ <- allDeviceUsers.traverse_(user =>
        Task(keycloakDeviceConnector.keycloak.realm(DEVICE_REALM).users().delete(user.getId)))
    } yield ()
  }

  def assignCredentialsToUser(userId: UserId, password: String)(
    userService: KeycloakUserService,
    keycloakConnector: CertifyKeycloakConnector): Task[Unit] = {
    val credentialRepresentation = new CredentialRepresentation()
    credentialRepresentation.setType(CredentialRepresentation.PASSWORD)
    credentialRepresentation.setValue(password)
    credentialRepresentation.setTemporary(false)

    for {
      maybeUser <- userService.getUserById(userId, CertifyKeycloak)
      user = maybeUser.getOrElse(fail(s"Expected to get user with username $userId"))
      _ = user.setCredentials(List(credentialRepresentation).asJava)
      _ <- Task(keycloakConnector.keycloak.realm(CERTIFY_REALM).users().get(user.getId).update(user))
    } yield ()
  }

  def createKeycloakAdminUser(clientAdmin: TenantAdmin)(keycloakConnector: CertifyKeycloakConnector): Task[Unit] =
    Task {
      val userRepresentation = new UserRepresentation()
      userRepresentation.setFirstName(clientAdmin.userName.value)
      userRepresentation.setLastName(clientAdmin.userName.value)
      userRepresentation.setEmail(clientAdmin.email)
      val credentialRepresentation = new CredentialRepresentation()
      credentialRepresentation.setType(CredentialRepresentation.PASSWORD)
      credentialRepresentation.setValue(clientAdmin.password)
      credentialRepresentation.setTemporary(false)
      userRepresentation.setCredentials(List(credentialRepresentation).asJava)
      userRepresentation.setEnabled(true)
      keycloakConnector.keycloak.realm(CERTIFY_REALM).users().create(userRepresentation)

      val admin = keycloakConnector.keycloak.realm(CERTIFY_REALM).users().list().asScala.toList.filter(
        _.getEmail == clientAdmin.email.toLowerCase).get(0)
      val adminRole = keycloakConnector.keycloak.realm(CERTIFY_REALM).roles().get("admin")
      keycloakConnector.keycloak
        .realm(CERTIFY_REALM)
        .users()
        .get(admin.get.getId)
        .roles()
        .realmLevel()
        .add(List(adminRole.toRepresentation).asJava)
    }

  def setConfirmationMailSentAttribute(value: Boolean, userId: UserId)(
    userService: KeycloakUserService,
    keycloakConnector: CertifyKeycloakConnector): Task[Unit] = {
    for {
      maybeUser <- userService.getUserById(userId, CertifyKeycloak)
      user = maybeUser.getOrElse(fail(s"Expected to find user with username $userId in Keycloak"))
      _ = user.setAttributes(Map("confirmation_mail_sent" -> List(value.toString).asJava).asJava)
      _ <- Task(keycloakConnector.keycloak.realm(CERTIFY_REALM).users().get(user.getId).update(user))
    } yield ()
  }

  def setEmailVerified(
    userId: UserId)(userService: KeycloakUserService, keycloakConnector: CertifyKeycloakConnector): Task[Unit] = {
    for {
      maybeUser <- userService.getUserById(userId, CertifyKeycloak)
      user = maybeUser.getOrElse(fail(s"Expected to find user with username $userId in Keycloak"))
      _ = user.setEmailVerified(true)
      _ <- Task(keycloakConnector.keycloak.realm(CERTIFY_REALM).users().get(user.getId).update(user))
    } yield ()
  }
}

case class TenantAdmin(userName: UserName, email: String, password: String)

case class SuperAdmin(userName: UserName, password: String)
