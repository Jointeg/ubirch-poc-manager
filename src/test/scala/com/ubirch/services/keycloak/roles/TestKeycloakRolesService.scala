package com.ubirch.services.keycloak.roles
import com.ubirch.models.keycloak.roles.{ CreateKeycloakRole, KeycloakRole, RoleAlreadyExists, RoleId, RoleName }
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakRolesService() extends KeycloakRolesService {
  private val rolesDatastore = mutable.Map[RoleName, KeycloakRole]()

  override def createNewRole(createKeycloakRole: CreateKeycloakRole): Task[Either[RoleAlreadyExists, Unit]] =
    Task {
      rolesDatastore.find(_._1 == createKeycloakRole.roleName) match {
        case Some(_) => Left(RoleAlreadyExists(createKeycloakRole.roleName))
        case None =>
          rolesDatastore += (
            (
              createKeycloakRole.roleName,
              KeycloakRole(RoleId(UUID.randomUUID().toString), createKeycloakRole.roleName)))
          Right(())
      }
    }
  override def findRole(roleName: RoleName): Task[Option[KeycloakRole]] = Task(rolesDatastore.get(roleName))
  override def deleteRole(roleName: RoleName): Task[Unit] =
    Task {
      rolesDatastore -= roleName
      ()
    }
}
