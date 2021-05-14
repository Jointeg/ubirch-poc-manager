package com.ubirch.e2e.keycloak

import com.ubirch.data.KeycloakTestData
import com.ubirch.data.KeycloakTestData.createNewKeycloakGroup
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupName, GroupNotFound }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleAlreadyExists, RoleName }
import com.ubirch.models.keycloak.user.UserAlreadyExists
import com.ubirch.models.user.UserName
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import org.keycloak.representations.idm.GroupRepresentation
import org.scalactic.StringNormalizations._

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

class KeycloakIntegrationTest extends E2ETestBase {

  "KeycloakGroupService" should {
    "Create new group, find it and then delete" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val newGroup = createNewKeycloakGroup()
        val res = for {
          _ <- keycloakGroupService.createGroup(newGroup)
          foundGroup <- keycloakGroupService.findGroupByName(newGroup.groupName)
          _ <- keycloakGroupService.deleteGroup(newGroup.groupName)
          groupAfterDeletion <- keycloakGroupService.findGroupByName(newGroup.groupName)
        } yield (foundGroup, groupAfterDeletion)

        val (maybeFoundGroup, groupAfterDeletion) = await(res, 2.seconds)
        maybeFoundGroup.right.value.getName shouldBe newGroup.groupName.value
        groupAfterDeletion.left.value shouldBe GroupNotFound(newGroup.groupName)
      }
    }

    "Create group hierarchies with roles and find them" in {
      withInjector { injector =>
        val groups = injector.get[KeycloakGroupService]
        val roles = injector.get[KeycloakRolesService]

        val tenantName = "T_tenantName"
        val pocName = "P_pocName"
        val tenantPath = s"/$tenantName"
        val childPocPath = s"$tenantPath/$pocName"

        val res1 = for {

          //create tenant role and group
          _ <- roles.createNewRole(CreateKeycloakRole(RoleName(tenantName)))
          tenantRole <- roles.findRoleRepresentation(RoleName(tenantName))
          tenantGroup <- groups.createGroup(CreateKeycloakGroup(GroupName(tenantName)))
          - <- groups.addRoleToGroup(tenantGroup.right.value, tenantRole.get)

          //create poc role and create as subGroup of tenantgroup
          _ <- roles.createNewRole(CreateKeycloakRole(RoleName(pocName)))
          pocRole <- roles.findRoleRepresentation(RoleName(pocName))
          pocGroup <- groups.addSubGroup(tenantGroup.right.value, GroupName(pocName))
          - <- groups.addRoleToGroup(pocGroup.right.value, pocRole.get)

          //retrieve final Groups
          tenantGroupFinal <- groups.findGroupById(tenantGroup.right.value)
          pocGroupFinal <- groups.findGroupById(pocGroup.right.value)

        } yield {
          (tenantGroupFinal, pocGroupFinal)
        }

        val (tenantGroup, pocGroup) = await(res1, 5.seconds)

        tenantGroup.right.value.getName shouldBe tenantName
        tenantGroup.right.value.getPath shouldBe tenantPath
        tenantGroup.right.value.getRealmRoles shouldBe Array(tenantName).toList.asJava

        val list = tenantGroup.right.value.getSubGroups
        list.get(0).getName shouldBe pocName
        list.get(0).getPath shouldBe childPocPath

        pocGroup.right.value.getName shouldBe pocName
        pocGroup.right.value.getPath shouldBe childPocPath
        pocGroup.right.value.getRealmRoles shouldBe List(pocName).asJava
      }
    }

    "Retrieve id of child group, if already exists" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val parentGroup = createNewKeycloakGroup()
        val childGroup = createNewKeycloakGroup()
        val res = for {
          parentGroup <- keycloakGroupService.createGroup(parentGroup)
          first <- keycloakGroupService.addSubGroup(parentGroup.right.value, childGroup.groupName)
          second <- keycloakGroupService.addSubGroup(parentGroup.right.value, childGroup.groupName)
        } yield (first, second)

        val (firstGroup, secondGroup) = await(res, 2.seconds)
        firstGroup.isRight shouldBe true
        secondGroup.isRight shouldBe true
        secondGroup.right.value shouldBe firstGroup.right.value
      }
    }

    "Retrieve id of group, if already exists" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val newGroup = createNewKeycloakGroup()
        val res = for {
          first <- keycloakGroupService.createGroup(newGroup, DeviceKeycloak)
          second <- keycloakGroupService.createGroup(newGroup, DeviceKeycloak)
        } yield (first, second)

        val (firstGroup, secondGroup) = await(res, 2.seconds)
        firstGroup.isRight shouldBe true
        secondGroup.isRight shouldBe true
        secondGroup.right.value shouldBe firstGroup.right.value
      }
    }

    "Not be able to retrieve info about group that does not exists" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val groupName = GroupName("Unknown group")
        val result = keycloakGroupService.findGroupByName(groupName)

        val maybeGroup = await(result, 2.seconds)
        maybeGroup.left.value shouldBe GroupNotFound(groupName)
      }
    }

    "Do nothing when tries to delete unknown group" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val newGroup = createNewKeycloakGroup()
        val res = for {
          _ <- keycloakGroupService.createGroup(newGroup)
          _ <- keycloakGroupService.deleteGroup(GroupName("Unknown group"))
          foundGroup <- keycloakGroupService.findGroupByName(newGroup.groupName)
        } yield foundGroup

        val maybeFoundGroup = await(res, 2.seconds)
        maybeFoundGroup.right.value.getName shouldBe newGroup.groupName.value
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
        firstCreationResult.isRight shouldBe true
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

        firstCreationResult.isRight shouldBe true
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

    "Assign group to user successfully" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val group = new GroupRepresentation()
        group.setName("testGroup")
        val result = for {
          user <- users.createUser(newKeycloakUser)
          success <- users.addGroupToUser(user.right.get.value.toString, group)
        } yield success

        result.runSyncUnsafe() shouldBe Right(())
      }
    }

    "Assign group to user twice succeeds" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val group = new GroupRepresentation()
        group.setName("testGroup")
        val result = for {
          user <- users.createUser(newKeycloakUser)
          _ <- users.addGroupToUser(user.right.get.value.toString, group)
          success <- users.addGroupToUser(user.right.get.value.toString, group)
        } yield success
        result.runSyncUnsafe() shouldBe Right(())
      }
    }

    "Assign group to non existing user should fail" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val userId = UUID.randomUUID().toString
        val group = new GroupRepresentation()
        group.setName("testGroup")
        val result = for {
          success <- users.addGroupToUser(userId, group)
        } yield (group, success)
        val (g, s) = result.runSyncUnsafe()
        s shouldBe Left(s"failed to add group ${g.getName} to user $userId")
      }
    }
  }

  "Double Keycloak integration" should {
    "allow to create same users (Name/Email/Username etc.) in Device and Users Keycloak" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val result = for {
          res1 <- keycloakUserService.createUser(newKeycloakUser, CertifyKeycloak)
          res2 <- keycloakUserService.createUser(newKeycloakUser, DeviceKeycloak)
        } yield (res1, res2)

        val (usr1, usr2) = await(result, 5.seconds)
        usr1.isRight shouldBe true
        usr2.isRight shouldBe true
      }
    }

    "delete user only from Keycloak that was asked for" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewKeycloakUser()
        val result = for {
          _ <- keycloakUserService.createUser(newKeycloakUser, CertifyKeycloak)
          _ <- keycloakUserService.createUser(newKeycloakUser, DeviceKeycloak)
          _ <- keycloakUserService.deleteUser(newKeycloakUser.userName, CertifyKeycloak)
          deletedUser <- keycloakUserService.getUser(newKeycloakUser.userName, CertifyKeycloak)
          existingUser <- keycloakUserService.getUser(newKeycloakUser.userName, DeviceKeycloak)
        } yield (deletedUser, existingUser)

        val (deletedUser, existingUser) = await(result, 5.seconds)
        deletedUser shouldBe None
        existingUser shouldBe defined
      }
    }
  }

}
