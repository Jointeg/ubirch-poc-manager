package com.ubirch.services.keycloak.groups

import com.ubirch.models.keycloak.group._
import com.ubirch.services.{ DeviceKeycloak, KeycloakInstance, UsersKeycloak }
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakGroupsService() extends KeycloakGroupService {
  private val groupsUsersDatastore = mutable.Map[GroupName, KeycloakGroup]()
  private val groupsDeviceDatastore = mutable.Map[GroupName, KeycloakGroup]()

  override def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupAlreadyExists, Unit]] =
    keycloakInstance match {
      case UsersKeycloak  => insertIfNotExists(groupsUsersDatastore, createKeycloakGroup)
      case DeviceKeycloak => insertIfNotExists(groupsDeviceDatastore, createKeycloakGroup)
    }

  private def insertIfNotExists(
    datastore: mutable.Map[GroupName, KeycloakGroup],
    createKeycloakGroup: CreateKeycloakGroup) = {
    Task {
      datastore.find(_._1 == createKeycloakGroup.groupName) match {
        case Some(_) => Left(GroupAlreadyExists(createKeycloakGroup.groupName))
        case None =>
          datastore += (
            (
              createKeycloakGroup.groupName,
              KeycloakGroup(GroupId(UUID.randomUUID().toString), createKeycloakGroup.groupName)))
          Right(())
      }
    }
  }

  override def findGroup(
    groupName: GroupName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupNotFound, KeycloakGroup]] =
    keycloakInstance match {
      case UsersKeycloak  => findInDatastore(groupsUsersDatastore, groupName)
      case DeviceKeycloak => findInDatastore(groupsDeviceDatastore, groupName)
    }

  private def findInDatastore(datastore: mutable.Map[GroupName, KeycloakGroup], groupName: GroupName) = {
    Task {
      datastore.get(groupName) match {
        case Some(keycloakGroup) => Right(keycloakGroup)
        case None                => Left(GroupNotFound(groupName))
      }
    }
  }

  override def deleteGroup(groupName: GroupName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit] =
    keycloakInstance match {
      case UsersKeycloak =>
        Task {
          groupsUsersDatastore -= groupName
          ()
        }
      case DeviceKeycloak =>
        Task {
          groupsDeviceDatastore -= groupName
          ()
        }
    }
}
