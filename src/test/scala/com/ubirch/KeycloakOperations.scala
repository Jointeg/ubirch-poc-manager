package com.ubirch

import cats.implicits._
import com.ubirch.models.keycloak.user.UserName
import com.ubirch.services.keycloak.KeycloakConnector
import com.ubirch.services.keycloak.users.KeycloakUserService
import monix.eval.Task
import org.keycloak.representations.idm.{CredentialRepresentation, UserRepresentation}
import org.scalatest.Matchers.fail
import org.scalatest.{EitherValues, OptionValues}

import scala.collection.JavaConverters.{iterableAsScalaIterableConverter, mapAsJavaMapConverter, seqAsJavaListConverter}
import scala.util.Random

trait KeycloakOperations extends ExecutionContextsTests with Awaits with OptionValues with EitherValues {

  val TEST_REALM = "test-realm"

  def cleanAllUsers(keycloakConnector: KeycloakConnector): Task[Unit] = {
    for {
      allUsers <- Task(keycloakConnector.keycloak.realm(TEST_REALM).users().list().asScala.toList)
      _ <- allUsers.traverse_(user => Task(keycloakConnector.keycloak.realm(TEST_REALM).users().delete(user.getId)))
    } yield ()
  }

  def assignCredentialsToUser(username: String, password: String)(
    userService: KeycloakUserService,
    keycloakConnector: KeycloakConnector): Task[Unit] = {
    val credentialRepresentation = new CredentialRepresentation()
    credentialRepresentation.setType(CredentialRepresentation.PASSWORD)
    credentialRepresentation.setValue(password)
    credentialRepresentation.setTemporary(false)

    for {
      maybeUser <- userService.getUser(UserName(username))
      user = maybeUser.getOrElse(fail(s"Expected to get user with username $username"))
      _ = user.setCredentials(List(credentialRepresentation).asJava)
      _ <- Task(keycloakConnector.keycloak.realm(TEST_REALM).users().get(user.getId).update(user))
    } yield ()
  }

  def createKeycloakAdminUser(clientAdmin: ClientAdmin)(keycloakConnector: KeycloakConnector): Task[Unit] =
    Task {
      val userRepresentation = new UserRepresentation()
      userRepresentation.setUsername(clientAdmin.userName.value)
      userRepresentation.setFirstName(clientAdmin.userName.value)
      userRepresentation.setLastName(clientAdmin.userName.value)
      userRepresentation.setEmail(s"${Random.alphanumeric.take(10).mkString("")}@email.com")
      val credentialRepresentation = new CredentialRepresentation()
      credentialRepresentation.setType(CredentialRepresentation.PASSWORD)
      credentialRepresentation.setValue(clientAdmin.password)
      credentialRepresentation.setTemporary(false)
      userRepresentation.setCredentials(List(credentialRepresentation).asJava)
      userRepresentation.setEnabled(true)
      keycloakConnector.keycloak.realm(TEST_REALM).users().create(userRepresentation)

      val admin = keycloakConnector.keycloak.realm(TEST_REALM).users().search(clientAdmin.userName.value).get(0)
      val adminRole = keycloakConnector.keycloak.realm(TEST_REALM).roles().get("admin")
      keycloakConnector.keycloak
        .realm(TEST_REALM)
        .users()
        .get(admin.getId)
        .roles()
        .realmLevel()
        .add(List(adminRole.toRepresentation).asJava)
    }

  def setConfirmationMailSentAttribute(value: Boolean, username: UserName)(
    userService: KeycloakUserService,
    keycloakConnector: KeycloakConnector): Task[Unit] = {
    for {
      maybeUser <- userService.getUser(username)
      user = maybeUser.getOrElse(fail(s"Expected to find user with username $username in Keycloak"))
      _ = user.setAttributes(Map("confirmation_mail_sent" -> List(value.toString).asJava).asJava)
      _ <- Task(keycloakConnector.keycloak.realm(TEST_REALM).users().get(user.getId).update(user))
    } yield ()
  }

  def setEmailVerified(
    username: UserName)(userService: KeycloakUserService, keycloakConnector: KeycloakConnector): Task[Unit] = {
    for {
      maybeUser <- userService.getUser(username)
      user = maybeUser.getOrElse(fail(s"Expected to find user with username $username in Keycloak"))
      _ = user.setEmailVerified(true)
      _ <- Task(keycloakConnector.keycloak.realm(TEST_REALM).users().get(user.getId).update(user))
    } yield ()
  }
}

case class ClientAdmin(userName: UserName, password: String)
