package com.ubirch.services.keycloak.users;

import com.ubirch.KeycloakBasedTest
import com.ubirch.data.KeycloakTestData
import com.ubirch.services.keycloak.KeycloakConnector
import monix.eval.Task
import monix.reactive.Observable
import org.scalatest.concurrent.{Eventually, IntegrationPatience}

import scala.concurrent.duration.DurationInt;

class KeycloakUserPollingServiceTest extends KeycloakBasedTest with Eventually with IntegrationPatience {

  "Keycloak User Polling Service" should {
    "Poll only users that have verified email and confirmation_mail_sent attribute set to false" in {
      withInjector { injector =>
        val userPollingService = injector.get[UserPollingService]
        val userService = injector.get[KeycloakUserService]
        val keycloakConnector = injector.get[KeycloakConnector]

        createKeycloakAdminUser("client-admin", "client-admin")(keycloakConnector)
        val keycloakUser1 = KeycloakTestData.createNewKeycloakUser()
        val keycloakUser2 = KeycloakTestData.createNewKeycloakUser()
        val keycloakUser3 = KeycloakTestData.createNewKeycloakUser()

        val pollingResult = for {
          pollingFiber <- userPollingService.via(Observable(_)).takeByTimespan(2.seconds).lastL.start
          _ <- userService.createUser(keycloakUser1)
          _ <- userService.createUser(keycloakUser2)
          _ <- userService.createUser(keycloakUser3)
          _ <- setEmailVerified(keycloakUser1.userName)(userService, keycloakConnector)
          _ <- setEmailVerified(keycloakUser3.userName)(userService, keycloakConnector)
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
        val keycloakConnector = injector.get[KeycloakConnector]

        createKeycloakAdminUser("client-admin", "client-admin")(keycloakConnector)
        val keycloakUser1 = KeycloakTestData.createNewKeycloakUser()
        val keycloakUser2 = KeycloakTestData.createNewKeycloakUser()
        val keycloakUser3 = KeycloakTestData.createNewKeycloakUser()

        val pollingResult = for {
          pollingFiber <- userPollingService.via(Observable(_)).takeByTimespan(4.seconds).toListL.start
          _ <- userService.createUser(keycloakUser1)
          _ <- userService.createUser(keycloakUser2)
          _ <- userService.createUser(keycloakUser3)
          _ <- setEmailVerified(keycloakUser1.userName)(userService, keycloakConnector)
          _ <- setEmailVerified(keycloakUser2.userName)(userService, keycloakConnector)
          _ <- setEmailVerified(keycloakUser3.userName)(userService, keycloakConnector)
          _ <- Task.sleep(1500.millis)
          _ <- setConfirmationMailSentAttribute(true, keycloakUser1.userName)(userService, keycloakConnector)
          _ <- setConfirmationMailSentAttribute(true, keycloakUser3.userName)(userService, keycloakConnector)
          pollingResult <- pollingFiber.join
        } yield pollingResult

        val listOfPolledUsers = await(pollingResult, 10.seconds).map {
          case Left(exception) => fail(s"Did not expect to get exception while polling users: ${exception.getMessage}")
          case Right(polledUsers) => polledUsers
        }

        // At some point there should be 3 users with email verified and confirmation_mail_sent = false
        listOfPolledUsers.find(_.size == 3) shouldBe defined
        // After "sending" mail to two users, polling service should poll only one user that is still awaiting
        listOfPolledUsers.last.size shouldBe 1
      }
    }
  }

}
