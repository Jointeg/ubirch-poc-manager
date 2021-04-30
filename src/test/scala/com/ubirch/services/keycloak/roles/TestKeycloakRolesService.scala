package com.ubirch.services.keycloak.roles
import com.ubirch.models.keycloak.roles._
import com.ubirch.services.{ DeviceKeycloak, KeycloakInstance, UsersKeycloak }
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakRolesService() extends KeycloakRolesService {
  private val rolesUserDatastore = mutable.Map[RoleName, KeycloakRole]()
  private val rolesDeviceDatastore = mutable.Map[RoleName, KeycloakRole]()

  override def createNewRole(
    createKeycloakRole: CreateKeycloakRole,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[RoleAlreadyExists, Unit]] =
    keycloakInstance match {
      case UsersKeycloak  => insertIfNotExists(rolesUserDatastore, createKeycloakRole)
      case DeviceKeycloak => insertIfNotExists(rolesDeviceDatastore, createKeycloakRole)
    }

  private def insertIfNotExists(
    datastore: mutable.Map[RoleName, KeycloakRole],
    createKeycloakRole: CreateKeycloakRole) = {
    Task {
      datastore.find(_._1 == createKeycloakRole.roleName) match {
        case Some(_) => Left(RoleAlreadyExists(createKeycloakRole.roleName))
        case None =>
          datastore += (
            (
              createKeycloakRole.roleName,
              KeycloakRole(RoleId(UUID.randomUUID().toString), createKeycloakRole.roleName)))
          Right(())
      }
    }
  }
  override def findRole(
    roleName: RoleName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Option[KeycloakRole]] =
    keycloakInstance match {
      case UsersKeycloak  => Task(rolesUserDatastore.get(roleName))
      case DeviceKeycloak => Task(rolesDeviceDatastore.get(roleName))
    }
  override def deleteRole(roleName: RoleName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit] =
    keycloakInstance match {
      case UsersKeycloak =>
        Task {
          rolesUserDatastore -= roleName
          ()
        }
      case DeviceKeycloak =>
        Task {
          rolesDeviceDatastore -= roleName
          ()
        }
    }
}
