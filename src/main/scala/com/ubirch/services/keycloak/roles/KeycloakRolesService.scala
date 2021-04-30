package com.ubirch.services.keycloak.roles

import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.roles._
import com.ubirch.services.{ KeycloakConnector, KeycloakInstance, UsersKeycloak }
import monix.eval.Task

import javax.ws.rs.{ ClientErrorException, NotFoundException }

trait KeycloakRolesService {
  def createNewRole(
    createKeycloakRole: CreateKeycloakRole,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[RoleAlreadyExists, Unit]]
  def findRole(roleName: RoleName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Option[KeycloakRole]]
  def deleteRole(roleName: RoleName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit]
}

@Singleton
class DefaultKeycloakRolesService @Inject() (keycloakConnector: KeycloakConnector)
  extends KeycloakRolesService
  with LazyLogging {

  override def createNewRole(
    createKeycloakRole: CreateKeycloakRole,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[RoleAlreadyExists, Unit]] = {
    val roleRepresentation = createKeycloakRole.toKeycloakRepresentation

    Task(
      Right(
        keycloakConnector
          .getKeycloak(keycloakInstance)
          .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
          .roles()
          .create(roleRepresentation)
      )
    ).onErrorRecover {
      case exception: ClientErrorException if exception.getResponse.getStatus == 409 =>
        logger.error(s"Can't create role ${createKeycloakRole.roleName} as it already exists")
        Left(RoleAlreadyExists(createKeycloakRole.roleName))
    }
  }

  override def deleteRole(roleName: RoleName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit] = {
    Task(
      keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .roles()
        .deleteRole(roleName.value)
    ).onErrorRecover {
      case _: NotFoundException =>
        logger.error(s"Tried to delete not existing role $roleName")
        ()
    }
  }

  override def findRole(
    roleName: RoleName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Option[KeycloakRole]] = {
    Task {
      val keycloakRoleRepresentation = keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
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
