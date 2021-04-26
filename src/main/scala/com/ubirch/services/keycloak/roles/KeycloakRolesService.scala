package com.ubirch.services.keycloak.roles

import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.roles._
import com.ubirch.services.keycloak.{ KeycloakConfig, KeycloakConnector }
import monix.eval.Task

import javax.ws.rs.{ ClientErrorException, NotFoundException }

trait KeycloakRolesService {
  def createNewRole(createKeycloakRole: CreateKeycloakRole): Task[Either[RoleAlreadyExists, Unit]]
  def findRole(roleName: RoleName): Task[Option[KeycloakRole]]
  def deleteRole(roleName: RoleName): Task[Unit]
}

@Singleton
class DefaultKeycloakRolesService @Inject() (keycloakConnector: KeycloakConnector, keycloakConfig: KeycloakConfig)
  extends KeycloakRolesService
  with LazyLogging {

  override def createNewRole(createKeycloakRole: CreateKeycloakRole): Task[Either[RoleAlreadyExists, Unit]] = {
    val roleRepresentation = createKeycloakRole.toKeycloakRepresentation

    Task(
      Right(
        keycloakConnector.keycloak
          .realm(keycloakConfig.usersRealm)
          .roles()
          .create(roleRepresentation)
      )
    ).onErrorRecover {
      case exception: ClientErrorException if exception.getResponse.getStatus == 409 =>
        logger.error(s"Can't create role ${createKeycloakRole.roleName} as it already exists")
        Left(RoleAlreadyExists(createKeycloakRole.roleName))
    }
  }

  override def deleteRole(roleName: RoleName): Task[Unit] = {
    Task(
      keycloakConnector.keycloak
        .realm(keycloakConfig.usersRealm)
        .roles()
        .deleteRole(roleName.value)
    ).onErrorRecover {
      case _: NotFoundException =>
        logger.error(s"Tried to delete not existing role $roleName")
        ()
    }
  }

  override def findRole(roleName: RoleName): Task[Option[KeycloakRole]] = {
    Task {
      val keycloakRoleRepresentation = keycloakConnector.keycloak
        .realm(keycloakConfig.usersRealm)
        .roles()
        .get(roleName.value)
        .toRepresentation
      Some(KeycloakRole(RoleId(keycloakRoleRepresentation.getId), RoleName(keycloakRoleRepresentation.getName)))
    }.onErrorRecover {
      case _: NotFoundException =>
        logger.error(s"Could not find role with $roleName role name")
        None
    }
  }
}
