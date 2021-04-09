package com.ubirch

import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.ubirch.models.keycloak.user.UserName
import com.ubirch.services.keycloak.KeycloakConnector
import com.ubirch.services.keycloak.users.KeycloakUserService
import monix.eval.Task
import org.keycloak.representations.idm.{CredentialRepresentation, UserRepresentation}
import org.scalatest.{EitherValues, OptionValues}
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.collection.JavaConverters.{mapAsJavaMapConverter, seqAsJavaListConverter}
import scala.util.Random

trait KeycloakBasedTest
  extends ScalatraWordSpec
  with TestContainerForAll
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues {

  val TEST_REALM = "test-realm"

  override val containerDef: KeycloakContainer.Def = KeycloakContainer.Def()

  def withInjector[A](testCode: TestKeycloakInjectorHelperImpl => A): A = {
    withContainers { keycloakContainer =>
      testCode(new TestKeycloakInjectorHelperImpl(keycloakContainer))
    }
  }

  def createKeycloakAdminUser(username: String, password: String)(keycloakConnector: KeycloakConnector): Unit = {
    val userRepresentation = new UserRepresentation()
    userRepresentation.setUsername(username)
    userRepresentation.setFirstName(username)
    userRepresentation.setLastName(username)
    userRepresentation.setEmail(s"${Random.alphanumeric.take(10).mkString("")}@email.com")
    val credentialRepresentation = new CredentialRepresentation()
    credentialRepresentation.setType(CredentialRepresentation.PASSWORD)
    credentialRepresentation.setValue(password)
    credentialRepresentation.setTemporary(false)
    userRepresentation.setCredentials(List(credentialRepresentation).asJava)
    userRepresentation.setEnabled(true)
    keycloakConnector.keycloak.realm(TEST_REALM).users().create(userRepresentation)

    val clientAdmin = keycloakConnector.keycloak.realm(TEST_REALM).users().search(username).get(0)
    val adminRole = keycloakConnector.keycloak.realm(TEST_REALM).roles().get("admin")
    keycloakConnector.keycloak
      .realm(TEST_REALM)
      .users()
      .get(clientAdmin.getId)
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
