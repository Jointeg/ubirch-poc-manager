package com.ubirch.services.keycloak.group

import com.ubirch.E2ETestBase
import com.ubirch.data.KeycloakTestData.createNewKeycloakGroup
import com.ubirch.models.keycloak.group.{GroupAlreadyExists, GroupName, GroupNotFound}
import com.ubirch.services.keycloak.groups.KeycloakGroupService

import scala.concurrent.duration.DurationInt

class KeycloakGroupServiceTest extends E2ETestBase {

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

}
