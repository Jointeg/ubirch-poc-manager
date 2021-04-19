package com.ubirch.services.keycloak.roles

import com.ubirch.E2ETestBase
import com.ubirch.data.KeycloakTestData
import com.ubirch.models.keycloak.roles.{RoleAlreadyExists, RoleName}

import scala.concurrent.duration.DurationInt

class KeycloakRolesServiceTest extends E2ETestBase {

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
}
