package com.ubirch.e2e.keycloak

import com.ubirch.data.KeycloakTestData
import com.ubirch.data.KeycloakTestData.createNewKeycloakGroup
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.keycloak.group.{ CreateKeycloakGroup, GroupName, GroupNotFound }
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, RoleAlreadyExists, RoleName }
import com.ubirch.models.keycloak.user.{ UserAlreadyExists, UserRequiredAction }
import com.ubirch.models.user.UserName
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.roles.KeycloakRolesService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak }
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation }
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
        val realm = CertifyKeycloak.defaultRealm
        val res = for {
          _ <- keycloakGroupService.createGroup(realm, newGroup, CertifyKeycloak)
          foundGroup <- keycloakGroupService.findGroupByName(realm, newGroup.groupName, CertifyKeycloak)
          _ <- keycloakGroupService.deleteGroup(realm, newGroup.groupName, CertifyKeycloak)
          groupAfterDeletion <- keycloakGroupService.findGroupByName(realm, newGroup.groupName, CertifyKeycloak)
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
        val realm = CertifyKeycloak.defaultRealm
        val res1 = for {

          //create tenant role and group
          _ <- roles.createNewRole(realm, CreateKeycloakRole(RoleName(tenantName)), CertifyKeycloak)
          tenantRole <- roles.findRoleRepresentation(realm, RoleName(tenantName), CertifyKeycloak)
          tenantGroup <- groups.createGroup(realm, CreateKeycloakGroup(GroupName(tenantName)), CertifyKeycloak)
          - <- groups.assignRoleToGroup(realm, tenantGroup.right.value, tenantRole.get, CertifyKeycloak)

          //create poc role and create as subGroup of tenantgroup
          _ <- roles.createNewRole(realm, CreateKeycloakRole(RoleName(pocName)), CertifyKeycloak)
          pocRole <- roles.findRoleRepresentation(realm, RoleName(pocName), CertifyKeycloak)
          pocGroup <- groups.addSubGroup(realm, tenantGroup.right.value, GroupName(pocName), CertifyKeycloak)
          - <- groups.assignRoleToGroup(realm, pocGroup.right.value, pocRole.get, CertifyKeycloak)

          //retrieve final Groups
          tenantGroupFinal <- groups.findGroupById(realm, tenantGroup.right.value, CertifyKeycloak)
          pocGroupFinal <- groups.findGroupById(realm, pocGroup.right.value, CertifyKeycloak)

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

    "Assign role to group, if already exists" in {
      withInjector { injector =>
        val groups = injector.get[KeycloakGroupService]
        val roles = injector.get[KeycloakRolesService]

        val roleName = "test-role"
        val role = CreateKeycloakRole(RoleName(roleName))
        val group = createNewKeycloakGroup().copy(groupName = GroupName(roleName))
        val roleRepr = new RoleRepresentation()
        roleRepr.setName(roleName)
        val realm = CertifyKeycloak.defaultRealm

        val res = for {
          _ <- roles.createNewRole(realm, role, CertifyKeycloak)
          role <- roles.findRoleRepresentation(realm, RoleName(roleName), CertifyKeycloak)
          createdGroup <- groups.createGroup(realm, group, CertifyKeycloak)
          _ <- groups.assignRoleToGroup(realm, createdGroup.right.get, role.get, CertifyKeycloak)
          foundGroup <- groups.findGroupById(realm, createdGroup.right.get, CertifyKeycloak)
        } yield foundGroup

        val foundGroup: GroupRepresentation = await(res, 2.seconds).right.get
        foundGroup.getName shouldBe roleName
        foundGroup.getRealmRoles.get(0) shouldBe roleName
      }
    }

    "Retrieve id of child group, if already exists" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val parentGroup = createNewKeycloakGroup()
        val childGroup = createNewKeycloakGroup()
        val realm = CertifyKeycloak.defaultRealm
        val res = for {
          parentGroup <- keycloakGroupService.createGroup(realm, parentGroup, CertifyKeycloak)
          first <-
            keycloakGroupService.addSubGroup(realm, parentGroup.right.value, childGroup.groupName, CertifyKeycloak)
          second <-
            keycloakGroupService.addSubGroup(realm, parentGroup.right.value, childGroup.groupName, CertifyKeycloak)
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
          first <- keycloakGroupService.createGroup(DeviceKeycloak.defaultRealm, newGroup, DeviceKeycloak)
          second <- keycloakGroupService.createGroup(DeviceKeycloak.defaultRealm, newGroup, DeviceKeycloak)
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
        val result = keycloakGroupService.findGroupByName(CertifyKeycloak.defaultRealm, groupName, CertifyKeycloak)

        val maybeGroup = await(result, 2.seconds)
        maybeGroup.left.value shouldBe GroupNotFound(groupName)
      }
    }

    "Do nothing when tries to delete unknown group" in {
      withInjector { injector =>
        val keycloakGroupService = injector.get[KeycloakGroupService]

        val newGroup = createNewKeycloakGroup()
        val realm = CertifyKeycloak.defaultRealm
        val res = for {
          _ <- keycloakGroupService.createGroup(realm, newGroup, CertifyKeycloak)
          _ <- keycloakGroupService.deleteGroup(realm, GroupName("Unknown group"), CertifyKeycloak)
          foundGroup <- keycloakGroupService.findGroupByName(realm, newGroup.groupName, CertifyKeycloak)
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
        val realm = CertifyKeycloak.defaultRealm
        val response = for {
          _ <- keycloakRolesService.createNewRole(realm, newRole, CertifyKeycloak)
          foundRole <- keycloakRolesService.findRole(realm, newRole.roleName, CertifyKeycloak)
          _ <- keycloakRolesService.deleteRole(realm, newRole.roleName, CertifyKeycloak)
          roleAfterDeletion <- keycloakRolesService.findRole(realm, newRole.roleName, CertifyKeycloak)
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
        val realm = CertifyKeycloak.defaultRealm
        val response = for {
          firstCreationResult <- keycloakRolesService.createNewRole(realm, newRole, CertifyKeycloak)
          secondCreationResult <- keycloakRolesService.createNewRole(realm, newRole, CertifyKeycloak)
        } yield (firstCreationResult, secondCreationResult)

        val (firstCreationResult, secondCreationResult) = await(response, 2.seconds)
        firstCreationResult.isRight shouldBe true
        secondCreationResult.left.value shouldBe RoleAlreadyExists(newRole.roleName)
      }
    }

    "Not be able to retrieve info about unknown role" in {
      withInjector { injector =>
        val keycloakRolesService = injector.get[KeycloakRolesService]

        val response =
          keycloakRolesService.findRole(CertifyKeycloak.defaultRealm, RoleName("Unknown role"), CertifyKeycloak)

        val maybeFoundRole = await(response, 2.seconds)
        maybeFoundRole should not be defined
      }
    }

    "Do nothing when tries to delete unknown role" in {
      withInjector { injector =>
        val keycloakRolesService = injector.get[KeycloakRolesService]
        val newRole = KeycloakTestData.createNewKeycloakRole()
        val realm = CertifyKeycloak.defaultRealm
        val response = for {
          _ <- keycloakRolesService.createNewRole(realm, newRole, CertifyKeycloak)
          _ <- keycloakRolesService.deleteRole(realm, RoleName("Unknown role"), CertifyKeycloak)
          retrievedRole <- keycloakRolesService.findRole(realm, newRole.roleName, CertifyKeycloak)
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
        val realm = DeviceKeycloak.defaultRealm
        val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()

        val result = for {
          _ <- keycloakUserService.createUser(realm, newKeycloakUser, DeviceKeycloak)
          user <- keycloakUserService.getUserByUserName(realm, newKeycloakUser.userName, DeviceKeycloak)
          _ <- keycloakUserService.deleteUserByUserName(realm, newKeycloakUser.userName, DeviceKeycloak)
          userAfterDeletion <- keycloakUserService.getUserByUserName(realm, newKeycloakUser.userName, DeviceKeycloak)
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

        val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
        val result = for {
          firstCreationResult <-
            keycloakUserService.createUser(DeviceKeycloak.defaultRealm, newKeycloakUser, DeviceKeycloak)
          secondCreationResult <-
            keycloakUserService.createUser(DeviceKeycloak.defaultRealm, newKeycloakUser, DeviceKeycloak)
        } yield (firstCreationResult, secondCreationResult)

        val (firstCreationResult, secondCreationResult) = await(result, 5.seconds)

        firstCreationResult.isRight shouldBe true
        secondCreationResult.left.value shouldBe UserAlreadyExists(newKeycloakUser.userName.value)

      }
    }

    "Not be able to retrieve info about unknown user" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
        val result = for {
          _ <- keycloakUserService.createUser(DeviceKeycloak.defaultRealm, newKeycloakUser, DeviceKeycloak)
          maybeUser <-
            keycloakUserService.getUserByUserName(DeviceKeycloak.defaultRealm, UserName("unknownUser@notanemail.com"))
        } yield maybeUser

        val maybeUser = await(result, 5.seconds)
        maybeUser should not be defined
      }
    }

    "Do nothing when tries to delete unknown user" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
        val result = for {
          _ <- keycloakUserService.createUser(DeviceKeycloak.defaultRealm, newKeycloakUser, DeviceKeycloak)
          _ <- keycloakUserService.deleteUserByUserName(
            DeviceKeycloak.defaultRealm,
            UserName("unknownUser@notanemail.com"),
            DeviceKeycloak)
          maybeUser <- keycloakUserService.getUserByUserName(DeviceKeycloak.defaultRealm, newKeycloakUser.userName)
        } yield maybeUser

        val maybeUser = await(result, 5.seconds)
        maybeUser shouldBe defined
      }
    }

    "Assign group to user by name successfully" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val groups = injector.get[KeycloakGroupService]
        val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
        val result = for {
          group <- groups.createGroup(DeviceKeycloak.defaultRealm, createNewKeycloakGroup(), DeviceKeycloak)
          _ <- users.createUser(DeviceKeycloak.defaultRealm, newKeycloakUser, DeviceKeycloak)
          success <- users.addGroupToUserByName(
            DeviceKeycloak.defaultRealm,
            newKeycloakUser.userName.value,
            group.right.value.value,
            DeviceKeycloak)
        } yield success

        result.runSyncUnsafe() shouldBe Right(())
      }
    }

    "Assign group to user by id successfully" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val groups = injector.get[KeycloakGroupService]
        val newKeycloakUser = KeycloakTestData.createNewCertifyKeycloakUser()
        val result = for {
          group <- groups.createGroup(CertifyKeycloak.defaultRealm, createNewKeycloakGroup(), CertifyKeycloak)
          userId <- users.createUserWithoutUserName(CertifyKeycloak.defaultRealm, newKeycloakUser, CertifyKeycloak)
          success <- users.addGroupToUserById(
            CertifyKeycloak.defaultRealm,
            userId.right.get,
            group.right.value.value,
            CertifyKeycloak)
        } yield success

        result.runSyncUnsafe() shouldBe Right(())
      }
    }

    "Assign group to user twice succeeds" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
        val groups = injector.get[KeycloakGroupService]

        val result = for {
          group <- groups.createGroup(DeviceKeycloak.defaultRealm, createNewKeycloakGroup(), DeviceKeycloak)
          _ <- users.createUser(DeviceKeycloak.defaultRealm, newKeycloakUser, DeviceKeycloak)
          _ <- users.addGroupToUserByName(
            DeviceKeycloak.defaultRealm,
            newKeycloakUser.userName.value,
            group.right.get.value,
            DeviceKeycloak)
          success <- users.addGroupToUserByName(
            DeviceKeycloak.defaultRealm,
            newKeycloakUser.userName.value,
            group.right.get.value,
            DeviceKeycloak)
        } yield success
        result.runSyncUnsafe() shouldBe Right(())
      }
    }

    "Assign group to non existing user should fail" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val groups = injector.get[KeycloakGroupService]
        val group = new GroupRepresentation()
        group.setName("testGroup")
        val result = for {
          group <- groups.createGroup(DeviceKeycloak.defaultRealm, createNewKeycloakGroup(), DeviceKeycloak)
          success <- users.addGroupToUserByName(
            DeviceKeycloak.defaultRealm,
            "non-existing-user",
            group.right.get.value,
            DeviceKeycloak)
        } yield (group, success)
        val (g, s) = result.runSyncUnsafe()
        s shouldBe Left(s"user with name non-existing-user wasn't found")
      }
    }

    "Assign non-existing group to user should fail" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
        val group = new GroupRepresentation()
        group.setName("testGroup")
        val result = for {
          _ <- users.createUser(DeviceKeycloak.defaultRealm, newKeycloakUser, DeviceKeycloak)
          success <- users.addGroupToUserByName(
            DeviceKeycloak.defaultRealm,
            newKeycloakUser.userName.value,
            "non-existing-group",
            DeviceKeycloak)
        } yield (group, success)
        val (g, s) = result.runSyncUnsafe()
        s shouldBe Left(s"failed to add group non-existing-group to user ${newKeycloakUser.userName.value}")
      }
    }

    "Add actions to user successfully" in {
      withInjector { injector =>
        val users = injector.get[KeycloakUserService]
        val newKeycloakUser = KeycloakTestData.createNewCertifyKeycloakUser()
        val actions = List(
          UserRequiredAction.VERIFY_EMAIL,
          UserRequiredAction.WEBAUTHN_REGISTER,
          UserRequiredAction.UPDATE_PASSWORD)
        val result = for {
          userId <-
            users.createUserWithoutUserName(CertifyKeycloak.defaultRealm, newKeycloakUser, CertifyKeycloak, actions)
          user <- users.getUserById(CertifyKeycloak.defaultRealm, userId.right.get, CertifyKeycloak)
        } yield user
        val userOpt = result.runSyncUnsafe()
        assert(userOpt.isDefined)
        val addedActions = userOpt.get.getRequiredActions.asScala.toList
        assert(addedActions.nonEmpty)
        actions.foreach { action =>
          assert(addedActions.contains(action.toString))
        }
      }
    }

    "deactivate user" in withInjector { injector =>
      val keycloakUserService = injector.get[KeycloakUserService]
      val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
      val instance = CertifyKeycloak
      val realm = CertifyKeycloak.defaultRealm

      val r = for {
        create <- keycloakUserService.createUser(realm, newKeycloakUser, instance)
        id <- create match {
          case Left(err) =>
            Task.raiseError(new RuntimeException(s"Could not create user ${err.getClass.getSimpleName}"))
          case Right(id) => Task.pure(id)
        }
        deactivate <- keycloakUserService.deactivate(realm, id.value, instance)
        user <- deactivate match {
          case Left(e)  => Task.raiseError(new RuntimeException(e))
          case Right(_) => keycloakUserService.getUserById(realm, id, instance)
        }
      } yield user

      await(r).value.isEnabled shouldBe false
    }

    "activate user" in withInjector { injector =>
      val keycloakUserService = injector.get[KeycloakUserService]
      val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
      val instance = CertifyKeycloak
      val realm = CertifyKeycloak.defaultRealm

      val r = for {
        create <- keycloakUserService.createUser(realm, newKeycloakUser, instance)
        id <- create match {
          case Left(err) =>
            Task.raiseError(new RuntimeException(s"Could not create user ${err.getClass.getSimpleName}"))
          case Right(id) => Task.pure(id)
        }
        deactivate <- keycloakUserService.deactivate(realm, id.value, instance)
        userDeactivated <- deactivate match {
          case Left(e)  => Task.raiseError(new RuntimeException(e))
          case Right(_) => keycloakUserService.getUserById(realm, id, instance)
        }
        activate <- keycloakUserService.activate(realm, id.value, instance)
        userActivated <- activate match {
          case Left(e)  => Task.raiseError(new RuntimeException(e))
          case Right(_) => keycloakUserService.getUserById(realm, id, instance)
        }
      } yield (userDeactivated, userActivated)

      val result = await(r)
      result._1.value.isEnabled shouldBe false
      result._2.value.isEnabled shouldBe true
    }

    "remove 2fa token" in withInjector { injector =>
      val keycloakUserService = injector.get[KeycloakUserService]
      val newKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
      val instance = CertifyKeycloak
      val realm = CertifyKeycloak.defaultRealm
      val requiredAction = List(UserRequiredAction.UPDATE_PASSWORD, UserRequiredAction.WEBAUTHN_REGISTER)

      val r = for {
        create <- keycloakUserService.createUser(realm, newKeycloakUser, instance, requiredAction)
        id <- create match {
          case Left(err) =>
            Task.raiseError(new RuntimeException(s"Could not create user ${err.getClass.getSimpleName}"))
          case Right(id) => Task.pure(id)
        }
        userRequiredActionsBefore <- keycloakUserService.getUserById(realm, id, instance).flatMap {
          case Some(ur) => Task.pure(ur.getRequiredActions)
          case None     => Task.raiseError(new RuntimeException("User not found"))
        }
        _ <- keycloakUserService.remove2faToken(realm, id.value, instance)
        userRequiredActionsAfter <- keycloakUserService.getUserById(realm, id, instance).flatMap {
          case Some(ur) => Task.pure(ur.getRequiredActions)
          case None     => Task.raiseError(new RuntimeException("User not found"))
        }
      } yield (userRequiredActionsBefore, userRequiredActionsAfter)
      val (requiredActionsBefore, requiredActionsAfter) = await(r)

      requiredActionsBefore should contain theSameElementsAs List("UPDATE_PASSWORD", "webauthn-register")
      requiredActionsAfter should contain theSameElementsAs List("UPDATE_PASSWORD")
    }
  }

  "Double Keycloak integration" should {
    "allow to create same users (Name/Email etc.) in Device and Users Keycloak" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newDeviceKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
        val newCertifyKeycloakUser = KeycloakTestData.createNewCertifyKeycloakUser()
        val result = for {
          res1 <- keycloakUserService.createUserWithoutUserName(
            CertifyKeycloak.defaultRealm,
            newCertifyKeycloakUser,
            CertifyKeycloak)
          res2 <- keycloakUserService.createUser(DeviceKeycloak.defaultRealm, newDeviceKeycloakUser, DeviceKeycloak)
        } yield (res1, res2)

        val (usr1, usr2) = await(result, 5.seconds)
        usr1.isRight shouldBe true
        usr2.isRight shouldBe true
      }
    }

    "delete user only from Keycloak that was asked for" in {
      withInjector { injector =>
        val keycloakUserService = injector.get[KeycloakUserService]

        val newDeviceKeycloakUser = KeycloakTestData.createNewDeviceKeycloakUser()
        val newCertifyKeycloakUser = KeycloakTestData.createNewCertifyKeycloakUser()
        val result = for {
          _ <- keycloakUserService.createUser(DeviceKeycloak.defaultRealm, newDeviceKeycloakUser, DeviceKeycloak)
          userId <- keycloakUserService.createUserWithoutUserName(
            CertifyKeycloak.defaultRealm,
            newCertifyKeycloakUser,
            CertifyKeycloak)
          _ <- keycloakUserService.deleteUserByUserName(
            DeviceKeycloak.defaultRealm,
            newDeviceKeycloakUser.userName,
            DeviceKeycloak)
          deletedUser <- keycloakUserService.getUserByUserName(
            DeviceKeycloak.defaultRealm,
            newDeviceKeycloakUser.userName,
            DeviceKeycloak)
          existingUser <-
            keycloakUserService.getUserById(CertifyKeycloak.defaultRealm, userId.right.get, CertifyKeycloak)
        } yield (deletedUser, existingUser)

        val (deletedUser, existingUser) = await(result, 5.seconds)
        deletedUser shouldBe None
        existingUser shouldBe defined
      }
    }
  }

}
