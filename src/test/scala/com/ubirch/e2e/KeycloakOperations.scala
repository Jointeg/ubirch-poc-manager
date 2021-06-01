package com.ubirch.e2e

import cats.implicits._
import com.ubirch.data.KeycloakToken
import com.ubirch.models.user.{ UserId, UserName }
import com.ubirch.services.CertifyKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.keycloak.{ CertifyKeycloakConnector, DeviceKeycloakConnector }
import com.ubirch.{ Awaits, ExecutionContextsTests }
import monix.eval.Task
import org.json4s.Formats
import org.json4s.native.Serialization
import org.keycloak.representations.idm.{
  CredentialRepresentation,
  GroupRepresentation,
  RoleRepresentation,
  UserRepresentation
}
import org.scalatest.Matchers.fail
import org.scalatest.{ EitherValues, OptionValues }
import sttp.client._
import sttp.client.json4s._
import sttp.client.quick.backend

import javax.ws.rs.core
import scala.collection.JavaConverters.{
  iterableAsScalaIterableConverter,
  mapAsJavaMapConverter,
  seqAsJavaListConverter
}

trait KeycloakOperations extends ExecutionContextsTests with Awaits with OptionValues with EitherValues {

  implicit private val serialization: Serialization.type = org.json4s.native.Serialization

  val DEVICE_REALM = "ubirch-default-realm"
  val CERTIFY_REALM = "poc-certify"

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
      maybeUser <- userService.getUserById(CertifyKeycloak.defaultRealm, userId, CertifyKeycloak)
      user = maybeUser.getOrElse(fail(s"Expected to get user with username $userId"))
      _ = user.setCredentials(List(credentialRepresentation).asJava)
      _ <- Task(keycloakConnector.keycloak.realm(CERTIFY_REALM).users().get(user.getId).update(user))
    } yield ()
  }

  def getAuthToken(username: String, password: String, url: String)(implicit formats: Formats): String = {
    val response = basicRequest
      .body(
        Map(
          "client_id" -> "ubirch-2.0-user-access-local",
          "grant_type" -> "password",
          "client_secret" -> "ca942e9b-8336-43a3-bd22-adcaf7e5222f",
          "scope" -> "openid",
          "username" -> username,
          "password" -> password
        )
      )
      .post(uri"$url/realms/poc-certify/protocol/openid-connect/token")
      .response(asJson[KeycloakToken])
      .send()

    "bearer " + response.body.right.value.accessToken
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

  def createKeycloakTenantAdminUser(clientAdmin: TenantAdmin)(keycloakConnector: CertifyKeycloakConnector): Task[Unit] =
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
      val adminRole = keycloakConnector.keycloak.realm(CERTIFY_REALM).roles().get("tenant-admin")
      val tenantAdminRole =
        keycloakConnector.keycloak.realm(CERTIFY_REALM).roles().get(s"TEN_${clientAdmin.userName.value}")
      keycloakConnector.keycloak
        .realm(CERTIFY_REALM)
        .users()
        .get(admin.get.getId)
        .roles()
        .realmLevel()
        .add(List(adminRole.toRepresentation, tenantAdminRole.toRepresentation).asJava)
    }

  def createKeycloakSuperAdminUser(superAdmin: SuperAdmin)(keycloakConnector: CertifyKeycloakConnector): Task[Unit] =
    Task {
      val userRepresentation = new UserRepresentation()
      userRepresentation.setFirstName(superAdmin.userName.value)
      userRepresentation.setLastName(superAdmin.userName.value)
      userRepresentation.setEmail(superAdmin.email)
      val credentialRepresentation = new CredentialRepresentation()
      credentialRepresentation.setType(CredentialRepresentation.PASSWORD)
      credentialRepresentation.setValue(superAdmin.password)
      credentialRepresentation.setTemporary(false)
      userRepresentation.setCredentials(List(credentialRepresentation).asJava)
      userRepresentation.setEnabled(true)
      keycloakConnector.keycloak.realm(CERTIFY_REALM).users().create(userRepresentation)

      val admin = keycloakConnector.keycloak.realm(CERTIFY_REALM).users().list().asScala.toList.filter(
        _.getEmail == superAdmin.email.toLowerCase).get(0)
      val adminRole = keycloakConnector.keycloak.realm(CERTIFY_REALM).roles().get("super-admin")
      keycloakConnector.keycloak
        .realm(CERTIFY_REALM)
        .users()
        .get(admin.get.getId)
        .roles()
        .realmLevel()
        .add(List(adminRole.toRepresentation).asJava)
    }

  def createRole(roleName: String)(keycloakConnector: CertifyKeycloakConnector): Unit = {
    val roleRepresentation = new RoleRepresentation()
    roleRepresentation.setName(roleName)
    keycloakConnector.keycloak.realm(CERTIFY_REALM).roles().create(roleRepresentation)
  }

  def createGroupWithId(groupName: String)(keycloakConnector: DeviceKeycloakConnector): core.Response = {
    val groupRepresentation = new GroupRepresentation()
    groupRepresentation.setName(groupName)
    keycloakConnector.keycloak.realm(DEVICE_REALM).groups().add(groupRepresentation)
  }

  def updatePassword(userId: String, password: String)(keycloakConnector: CertifyKeycloakConnector): Unit = {
    val credentialRepresentation = new CredentialRepresentation()
    credentialRepresentation.setType(CredentialRepresentation.PASSWORD)
    credentialRepresentation.setValue(password)
    credentialRepresentation.setTemporary(false)
    val user = keycloakConnector.keycloak.realm(CERTIFY_REALM).users().get(userId).toRepresentation
    user.setCredentials(List(credentialRepresentation).asJava)
    keycloakConnector.keycloak.realm(CERTIFY_REALM).users().get(userId).update(user)
  }

  def setConfirmationMailSentAttribute(value: Boolean, userId: UserId)(
    userService: KeycloakUserService,
    keycloakConnector: CertifyKeycloakConnector): Task[Unit] = {
    for {
      maybeUser <- userService.getUserById(CertifyKeycloak.defaultRealm, userId, CertifyKeycloak)
      user = maybeUser.getOrElse(fail(s"Expected to find user with username $userId in Keycloak"))
      _ = user.setAttributes(Map("confirmation_mail_sent" -> List(value.toString).asJava).asJava)
      _ <- Task(keycloakConnector.keycloak.realm(CERTIFY_REALM).users().get(user.getId).update(user))
    } yield ()
  }

  def setEmailVerified(
    userId: UserId)(userService: KeycloakUserService, keycloakConnector: CertifyKeycloakConnector): Task[Unit] = {
    for {
      maybeUser <- userService.getUserById(CertifyKeycloak.defaultRealm, userId, CertifyKeycloak)
      user = maybeUser.getOrElse(fail(s"Expected to find user with username $userId in Keycloak"))
      _ = user.setEmailVerified(true)
      _ <- Task(keycloakConnector.keycloak.realm(CERTIFY_REALM).users().get(user.getId).update(user))
    } yield ()
  }
}

case class TenantAdmin(userName: UserName, email: String, password: String)

case class SuperAdmin(userName: UserName, email: String, password: String)
