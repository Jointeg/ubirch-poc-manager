package com.ubirch.e2e.keycloak.users
import com.ubirch.data.KeycloakTestData
import com.ubirch.e2e.{ E2ETestBase, KeycloakOperations }
import com.ubirch.models.keycloak.user.CreateKeycloakUser
import com.ubirch.models.user.{ Email, FirstName, LastName }
import com.ubirch.services.keycloak.users.{ KeycloakUserService, UserPollingService }
import com.ubirch.services.keycloak.{ CertifyKeycloakConnector, DeviceKeycloakConnector }
import monix.eval.Task
import monix.reactive.Observable
import sttp.client.HttpError
import sttp.model.StatusCode

import scala.concurrent.duration.DurationInt

class KeycloakUserPollingServiceTest extends E2ETestBase with KeycloakOperations {

  "Keycloak User Polling Service" should {
    "Poll only users that have verified email and confirmation_mail_sent attribute set to false" in {
      withInjector { injector =>
        val userPollingService = injector.get[UserPollingService]
        val userService = injector.get[KeycloakUserService]
        val certifyKeycloakConnector = injector.get[CertifyKeycloakConnector]
        val deviceKeycloakConnector = injector.get[DeviceKeycloakConnector]

        val keycloakUser1 = KeycloakTestData.createNewKeycloakUser()
        val keycloakUser2 = KeycloakTestData.createNewKeycloakUser()
        val keycloakUser3 = KeycloakTestData.createNewKeycloakUser()

        val pollingResult = for {
          _ <- cleanAllUsers(certifyKeycloakConnector, deviceKeycloakConnector)
          _ <- createKeycloakAdminUser(injector.tenantAdmin)(certifyKeycloakConnector)
          pollingFiber <- userPollingService.via(Observable(_)).takeByTimespan(3.seconds).lastL.start
          _ <- userService.createUser(keycloakUser1)
          _ <- userService.createUser(keycloakUser2)
          _ <- userService.createUser(keycloakUser3)
          _ <- setEmailVerified(keycloakUser1.userName)(userService, certifyKeycloakConnector)
          _ <- setEmailVerified(keycloakUser3.userName)(userService, certifyKeycloakConnector)
          pollingResult <- pollingFiber.join
        } yield pollingResult

        val result = await(pollingResult, 5.seconds)
        result.right.value.size shouldBe 2
        result.right.value.find(
          _.username.value.toLowerCase == keycloakUser1.userName.value.toLowerCase()) shouldBe defined
        result.right.value.find(
          _.username.value.toLowerCase() == keycloakUser3.userName.value.toLowerCase()) shouldBe defined
      }
    }

    "React to changes of confirmation_mail_sent attribute and stop polling users once it is set to true" in {
      withInjector { injector =>
        val userPollingService = injector.get[UserPollingService]
        val userService = injector.get[KeycloakUserService]
        val certifyKeycloakConnector = injector.get[CertifyKeycloakConnector]
        val deviceKeycloakConnector = injector.get[DeviceKeycloakConnector]

        createKeycloakAdminUser(injector.tenantAdmin)(certifyKeycloakConnector)
        val keycloakUser1 = KeycloakTestData.createNewKeycloakUser()
        val keycloakUser2 = KeycloakTestData.createNewKeycloakUser()
        val keycloakUser3 = KeycloakTestData.createNewKeycloakUser()

        val pollingResult = for {
          _ <- cleanAllUsers(certifyKeycloakConnector, deviceKeycloakConnector)
          _ <- createKeycloakAdminUser(injector.tenantAdmin)(certifyKeycloakConnector)
          pollingFiber <- userPollingService.via(Observable(_)).takeByTimespan(8.seconds).toListL.start
          _ <- userService.createUser(keycloakUser1)
          _ <- userService.createUser(keycloakUser2)
          _ <- userService.createUser(keycloakUser3)
          _ <- setEmailVerified(keycloakUser1.userName)(userService, certifyKeycloakConnector)
          _ <- setEmailVerified(keycloakUser2.userName)(userService, certifyKeycloakConnector)
          _ <- setEmailVerified(keycloakUser3.userName)(userService, certifyKeycloakConnector)
          _ <- Task.sleep(1500.millis)
          _ <-
            setConfirmationMailSentAttribute(value = true, keycloakUser1.userName)(
              userService,
              certifyKeycloakConnector)
          _ <-
            setConfirmationMailSentAttribute(value = true, keycloakUser3.userName)(
              userService,
              certifyKeycloakConnector)
          pollingResult <- pollingFiber.join
        } yield pollingResult

        val listOfPolledUsers = await(pollingResult, 10.seconds).map {
          case Left(exception)    => fail(s"Did not expect to get exception while polling users: ${exception.getMessage}")
          case Right(polledUsers) => polledUsers
        }

        // At some point there should be 3 users with email verified and confirmation_mail_sent = false
        listOfPolledUsers.find(_.size == 3) shouldBe defined
        // After "sending" mail to two users, polling service should poll only one user that is still awaiting
        listOfPolledUsers.last.size shouldBe 1
      }
    }

    "Deny access from user that does not have an admin role" in {
      withInjector { injector =>
        val userPollingService = injector.get[UserPollingService]
        val userService = injector.get[KeycloakUserService]
        val certifyKeycloakConnector = injector.get[CertifyKeycloakConnector]
        val deviceKeycloakConnector = injector.get[DeviceKeycloakConnector]

        val pollingResult = for {
          _ <- cleanAllUsers(certifyKeycloakConnector, deviceKeycloakConnector)
          _ <- userService.createUser(
            CreateKeycloakUser(
              FirstName(injector.tenantAdmin.userName.value),
              LastName(injector.tenantAdmin.userName.value),
              injector.tenantAdmin.userName,
              Email("test@email.com")))
          _ <- assignCredentialsToUser(injector.tenantAdmin.userName.value, injector.tenantAdmin.password)(
            userService,
            certifyKeycloakConnector)
          pollingResult <- userPollingService.via(Observable(_)).takeByTimespan(3.seconds).toListL
        } yield pollingResult

        val result = await(pollingResult, 5.seconds)
        result.foreach {
          case Left(exception) =>
            exception match {
              case httpError: HttpError if httpError.statusCode == StatusCode.Forbidden => ()
              case exception                                                            => fail(s"Expected to get HttpError indicating Unauthorized but instead got $exception")
            }
          case Right(value) => fail(s"Expected to retrieve error response from keycloak but instead got $value")
        }
      }
    }
  }

}
