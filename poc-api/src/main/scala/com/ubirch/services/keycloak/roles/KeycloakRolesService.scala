package com.ubirch.services.keycloak.roles

import com.google.inject.{ Inject, Singleton }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.roles._
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.{ KeycloakConnector, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.RoleRepresentation

import javax.ws.rs.{ ClientErrorException, NotFoundException }

trait KeycloakRolesService {
  def createNewRole(
    realm: KeycloakRealm,
    createKeycloakRole: CreateKeycloakRole,
    instance: KeycloakInstance): Task[Either[RolesException, Unit]]

  def findRole(realm: KeycloakRealm, roleName: RoleName, instance: KeycloakInstance): Task[Option[KeycloakRole]]

  def deleteRole(realm: KeycloakRealm, roleName: RoleName, instance: KeycloakInstance): Task[Unit]

  def findRoleRepresentation(
    realm: KeycloakRealm,
    roleName: RoleName,
    instance: KeycloakInstance): Task[Option[RoleRepresentation]]
}

@Singleton
class DefaultKeycloakRolesService @Inject() (keycloakConnector: KeycloakConnector)
  extends KeycloakRolesService
  with LazyLogging {

  override def createNewRole(
    realm: KeycloakRealm,
    createKeycloakRole: CreateKeycloakRole,
    instance: KeycloakInstance): Task[Either[RolesException, Unit]] = {
    val roleRepresentation = createKeycloakRole.toKeycloakRepresentation

    Task(
      Right(
        keycloakConnector
          .getKeycloak(instance)
          .realm(realm.name)
          .roles()
          .create(roleRepresentation)
      )
    ).onErrorRecover {
      case ex: ClientErrorException if ex.getResponse.getStatus == 409 =>
        logger.error(s"can't create role ${createKeycloakRole.roleName} as it already exists")
        Left(RoleAlreadyExists(createKeycloakRole.roleName))
      case ex: Exception =>
        logger.error(s"creation of role ${createKeycloakRole.roleName} failed ", ex)
        Left(RoleCreationException(createKeycloakRole.roleName))
    }
  }

  override def deleteRole(realm: KeycloakRealm, roleName: RoleName, instance: KeycloakInstance): Task[Unit] = {
    Task(
      keycloakConnector
        .getKeycloak(instance)
        .realm(realm.name)
        .roles()
        .deleteRole(roleName.value)
    ).onErrorRecover {
      case ex: NotFoundException =>
        logger.error(s"tried to delete not existing role $roleName", ex)
      case ex: Exception =>
        logger.error(s"could not find role with ${roleName.value} role name", ex)
        None
    }
  }

  override def findRole(
    realm: KeycloakRealm,
    roleName: RoleName,
    instance: KeycloakInstance): Task[Option[KeycloakRole]] = {
    Task {
      val keycloakRoleRepresentation =
        keycloakConnector
          .getKeycloak(instance)
          .realm(realm.name)
          .roles()
          .get(roleName.value)
          .toRepresentation
      Some(KeycloakRole(RoleId(keycloakRoleRepresentation.getId), RoleName(keycloakRoleRepresentation.getName)))
    }.onErrorRecover {
      case ex: NotFoundException =>
        logger.error(s"could not find role with ${roleName.value} role name", ex)
        None
      case ex: Exception =>
        logger.error(s"could not find role with ${roleName.value} role name", ex)
        None
    }
  }

  override def findRoleRepresentation(
    realm: KeycloakRealm,
    roleName: RoleName,
    instance: KeycloakInstance): Task[Option[RoleRepresentation]] = {
    Task {
      val keycloakRoleRepresentation = keycloakConnector
        .getKeycloak(instance)
        .realm(realm.name)
        .roles()
        .get(roleName.value)
        .toRepresentation
      Some(keycloakRoleRepresentation)
    }.onErrorRecover {
      case ex: NotFoundException =>
        logger.error(s"could not find role with ${roleName.value} role name", ex)
        None
      case ex: Exception =>
        logger.error(s"could not find role with ${roleName.value} role name", ex)
        None
    }
  }
}
