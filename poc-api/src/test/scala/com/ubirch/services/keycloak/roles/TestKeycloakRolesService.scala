package com.ubirch.services.keycloak.roles

import com.ubirch.models.keycloak.roles._
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.RoleRepresentation

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakRolesService() extends KeycloakRolesService {
  private val rolesCertifyDatastore = mutable.Map[RoleName, KeycloakRole]()
  private val rolesDeviceDatastore = mutable.Map[RoleName, KeycloakRole]()

  override def createNewRole(
    realm: KeycloakRealm,
    createKeycloakRole: CreateKeycloakRole,
    instance: KeycloakInstance): Task[Either[RoleAlreadyExists, Unit]] =
    instance match {
      case CertifyKeycloak => insertIfNotExists(rolesCertifyDatastore, createKeycloakRole)
      case DeviceKeycloak  => insertIfNotExists(rolesDeviceDatastore, createKeycloakRole)
    }

  private def insertIfNotExists(
    datastore: mutable.Map[RoleName, KeycloakRole],
    createKeycloakRole: CreateKeycloakRole) = {

    Task {
      val roleName = createKeycloakRole.roleName
      datastore.find(_._1 == roleName) match {
        case Some(_) => Left(RoleAlreadyExists(roleName))
        case None =>
          datastore += ((roleName, KeycloakRole(RoleId(UUID.randomUUID().toString), roleName)))
          Right(())
      }
    }
  }

  override def findRole(
    realm: KeycloakRealm,
    roleName: RoleName,
    instance: KeycloakInstance): Task[Option[KeycloakRole]] =
    instance match {
      case CertifyKeycloak => Task(rolesCertifyDatastore.get(roleName))
      case DeviceKeycloak  => Task(rolesDeviceDatastore.get(roleName))
    }

  override def deleteRole(realm: KeycloakRealm, roleName: RoleName, instance: KeycloakInstance): Task[Unit] =
    instance match {
      case CertifyKeycloak =>
        Task {
          rolesCertifyDatastore -= roleName
          ()
        }
      case DeviceKeycloak =>
        Task {
          rolesDeviceDatastore -= roleName
          ()
        }
    }

  override def findRoleRepresentation(
    realm: KeycloakRealm,
    roleName: RoleName,
    instance: KeycloakInstance): Task[Option[RoleRepresentation]] = {
    val opt = instance match {
      case CertifyKeycloak => rolesCertifyDatastore.get(roleName)
      case DeviceKeycloak  => rolesDeviceDatastore.get(roleName)
    }
    Task(opt.map { keycloakRole =>
      val role = new RoleRepresentation()
      role.setName(keycloakRole.roleName.value)
      role.setId(keycloakRole.id.value)
      role
    })
  }

}
