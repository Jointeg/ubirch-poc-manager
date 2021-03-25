package com.ubirch.services.keycloak

import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.ubirch.data.KeycloakTestData
import com.ubirch.models.keycloak.user.{UserAlreadyExists, UserName}
import com.ubirch.{Awaits, ExecutionContextsTests, KeycloakContainer, TestKeycloakInjectorHelperImpl}
import org.scalactic.StringNormalizations._
import org.scalatest.{Assertion, EitherValues, OptionValues}
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.concurrent.duration.DurationInt

class KeycloakUserServiceTest
  extends ScalatraWordSpec
  with TestContainerForAll
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues {
  override val containerDef: KeycloakContainer.Def = KeycloakContainer.Def()

  "KeycloakUserService" should {
    "Be able to create an user, retrieve info about him and delete him" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val result = for {
          _ <- keycloakUserService.createUser(newKeycloakUser)
          user <- keycloakUserService.getUser(newKeycloakUser.userName)
          _ <- keycloakUserService.deleteUser(newKeycloakUser.userName)
          userAfterDeletion <- keycloakUserService.getUser(newKeycloakUser.userName)
        } yield (user, userAfterDeletion)

        val (user, userAfterDeletion) = await(result, 5.seconds)

        user shouldBe defined
        user.value.getEmail should equal(newKeycloakUser.email.value)(after.being(lowerCased))
        user.value.getUsername should equal(newKeycloakUser.email.value)(after.being(lowerCased))
        user.value.getLastName should equal(newKeycloakUser.lastName.value)(after.being(lowerCased))
        user.value.getFirstName should equal(newKeycloakUser.firstName.value)(after.being(lowerCased))
        user.value.isEnabled shouldBe true

        userAfterDeletion should not be defined
      }
    }

    "Not be able to create a user with name that already exists in system" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val result = for {
          firstCreationResult <- keycloakUserService.createUser(newKeycloakUser)
          secondCreationResult <- keycloakUserService.createUser(newKeycloakUser)
        } yield (firstCreationResult, secondCreationResult)

        val (firstCreationResult, secondCreationResult) = await(result, 5.seconds)

        firstCreationResult.right.value shouldBe ()
        secondCreationResult.left.value shouldBe UserAlreadyExists(newKeycloakUser.userName)

      }
    }

    "Not be able to retrieve info about unknown user" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val result = for {
          _ <- keycloakUserService.createUser(newKeycloakUser)
          maybeUser <- keycloakUserService.getUser(UserName("unknownUser@notanemail.com"))
        } yield maybeUser

        val maybeUser = await(result, 5.seconds)
        maybeUser should not be defined
      }
    }

    "Do nothing when tries to delete unknown user" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val result = for {
          _ <- keycloakUserService.createUser(newKeycloakUser)
          _ <- keycloakUserService.deleteUser(UserName("unknownUser@notanemail.com"))
          maybeUser <- keycloakUserService.getUser(newKeycloakUser.userName)
        } yield maybeUser

        val maybeUser = await(result, 5.seconds)
        maybeUser shouldBe defined
      }
    }
  }

  private def withInjector(testCode: TestKeycloakInjectorHelperImpl => Assertion) = {
    withContainers { keycloakContainer =>
      testCode(new TestKeycloakInjectorHelperImpl(keycloakContainer))
    }
  }
}
