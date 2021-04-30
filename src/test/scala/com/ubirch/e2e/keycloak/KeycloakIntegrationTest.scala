package com.ubirch.e2e.keycloak

import com.ubirch.data.KeycloakTestData
import com.ubirch.data.KeycloakTestData.createNewKeycloakGroup
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.keycloak.group.{ GroupAlreadyExists, GroupName, GroupNotFound }
import com.ubirch.models.keycloak.roles.{ RoleAlreadyExists, RoleName }
import com.ubirch.models.keycloak.user.UserAlreadyExists
import com.ubirch.models.user.UserName
import com.ubirch.services.{ DeviceKeycloak, UsersKeycloak }
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.keycloak.users.KeycloakUserService
import org.scalactic.StringNormalizations._

import scala.concurrent.duration.DurationInt

class KeycloakIntegrationTest extends E2ETestBase {

  "KeycloakGroupService" should {
    "Create new group, find it and then delete" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val newGroup = createNewKeycloakGroup()
        val res = for {
          _ <- keycloakGroupService.createGroup(newGroup)
          foundGroup <- keycloakGroupService.findGroup(newGroup.groupName)
          _ <- keycloakGroupService.deleteGroup(newGroup.groupName)
          groupAfterDeletion <- keycloakGroupService.findGroup(newGroup.groupName)
        } yield (foundGroup, groupAfterDeletion)

        val (maybeFoundGroup, groupAfterDeletion) = await(res, 2.seconds)
        maybeFoundGroup.right.value.groupName shouldBe newGroup.groupName
        groupAfterDeletion.left.value shouldBe GroupNotFound(newGroup.groupName)
      }
    }

    "Not be able to create two groups with same names" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val newGroup = createNewKeycloakGroup()
        val res = for {
          firstGroup <- keycloakGroupService.createGroup(newGroup)
          secondGroup <- keycloakGroupService.createGroup(newGroup)
        } yield (firstGroup, secondGroup)

        val (maybeFirstGroup, maybeSecondGroup) = await(res, 2.seconds)
        maybeFirstGroup.right.value
        maybeSecondGroup.left.value shouldBe GroupAlreadyExists(newGroup.groupName)
      }
    }

    "Not be able to retrieve info about group that does not exists" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val result = keycloakGroupService.findGroup(GroupName("Unknown group"))

        val maybeGroup = await(result, 2.seconds)
        maybeGroup.left.value shouldBe GroupNotFound(GroupName("Unknown group"))
      }
    }

    "Do nothing when tries to delete unknown group" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val newGroup = createNewKeycloakGroup()
        val res = for {
          _ <- keycloakGroupService.createGroup(newGroup)
          _ <- keycloakGroupService.deleteGroup(GroupName("Unknown group"))
          foundGroup <- keycloakGroupService.findGroup(newGroup.groupName)
        } yield foundGroup

        val maybeFoundGroup = await(res, 2.seconds)
        maybeFoundGroup.right.value.groupName shouldBe newGroup.groupName
      }
    }
  }

  "KeycloakRolesService" should {
    "Be able to create, find and in the end delete role" in {
      withInjector { injector =>
        val keycloakRolesService = injector.get[KeycloakRolesService]
        val newRole = KeycloakTestData.createNewKeycloakRole()

        val response = for {
          _ <- keycloakRolesService.createNewRole(newRole)
          foundRole <- keycloakRolesService.findRole(newRole.roleName)
          _ <- keycloakRolesService.deleteRole(newRole.roleName)
          roleAfterDeletion <- keycloakRolesService.findRole(newRole.roleName)
        } yield (foundRole, roleAfterDeletion)

        val (maybeCreatedRole, roleAfterDeletion) = await(response, 2.seconds)
        maybeCreatedRole.value.roleName shouldBe newRole.roleName
        roleAfterDeletion should not be defined
      }
    }

    "Not be able to create a role with name that already exists in system" in {
      withInjector { injector =>
        val keycloakRolesService = injector.get[KeycloakRolesService]
        val newRole = KeycloakTestData.createNewKeycloakRole()

        val response = for {
          firstCreationResult <- keycloakRolesService.createNewRole(newRole)
          secondCreationResult <- keycloakRolesService.createNewRole(newRole)
        } yield (firstCreationResult, secondCreationResult)

        val (firstCreationResult, secondCreationResult) = await(response, 2.seconds)
        firstCreationResult.right.value
        secondCreationResult.left.value shouldBe RoleAlreadyExists(newRole.roleName)
      }
    }

    "Not be able to retrieve info about unknown role" in {
      withInjector { injector =>
        val keycloakRolesService = injector.get[KeycloakRolesService]

        val response = keycloakRolesService.findRole(RoleName("Unknown role"))

        val maybeFoundRole = await(response, 2.seconds)
        maybeFoundRole should not be defined
      }
    }

    "Do nothing when tries to delete unknown role" in {
      withInjector { injector =>
        val keycloakRolesService = injector.get[KeycloakRolesService]
        val newRole = KeycloakTestData.createNewKeycloakRole()

        val response = for {
          _ <- keycloakRolesService.createNewRole(newRole)
          _ <- keycloakRolesService.deleteRole(RoleName("Unknown role"))
          retrievedRole <- keycloakRolesService.findRole(newRole.roleName)
        } yield retrievedRole
        val maybeFoundRole = await(response, 2.seconds)

        maybeFoundRole.value.roleName shouldBe newRole.roleName
      }
    }
  }

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

        firstCreationResult.right.value
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

  "Double Keycloak integration" should {
    "allow to create same users (Name/Email/Username etc.) in Device and Users Keycloaks" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val result = for {
          res1 <- keycloakUserService.createUser(newKeycloakUser, UsersKeycloak)
          res2 <- keycloakUserService.createUser(newKeycloakUser, DeviceKeycloak)
        } yield (res1, res2)

        val (usr1, usr2) = await(result, 5.seconds)
        usr1 shouldBe Right(())
        usr2 shouldBe Right(())
      }
    }

    "delete user only from Keycloak that was asked for" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val result = for {
          _ <- keycloakUserService.createUser(newKeycloakUser, UsersKeycloak)
          _ <- keycloakUserService.createUser(newKeycloakUser, DeviceKeycloak)
          _ <- keycloakUserService.deleteUser(newKeycloakUser.userName, UsersKeycloak)
          deletedUser <- keycloakUserService.getUser(newKeycloakUser.userName, UsersKeycloak)
          existingUser <- keycloakUserService.getUser(newKeycloakUser.userName, DeviceKeycloak)
        } yield (deletedUser, existingUser)

        val (deletedUser, existingUser) = await(result, 5.seconds)
        deletedUser shouldBe None
        existingUser shouldBe defined
      }
    }
  }

}
