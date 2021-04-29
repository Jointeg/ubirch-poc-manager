package com.ubirch.services.keycloak.groups

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.group._
import com.ubirch.services.{KeycloakConnector, KeycloakInstance, UsersKeycloak}
import monix.eval.Task

import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

trait KeycloakGroupService {

  def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupAlreadyExists, Unit]]
  def findGroup(
    groupName: GroupName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupNotFound, KeycloakGroup]]
  def deleteGroup(groupName: GroupName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit]

}

class DefaultKeycloakGroupService @Inject() (keycloakConnector: KeycloakConnector)
  extends KeycloakGroupService
  with LazyLogging {
  override def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupAlreadyExists, Unit]] =
    Task {
      val response = keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .groups()
        .add(createKeycloakGroup.toKeycloakRepresentation)

      if (response.getStatus == 409) {
        logger.error(s"Could not create group with name ${createKeycloakGroup.groupName} as it already exists")
        Left(GroupAlreadyExists(createKeycloakGroup.groupName))
      } else {
        Right(())
      }
    }

  override def findGroup(
    groupName: GroupName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupNotFound, KeycloakGroup]] = {
    getGroupIdByName(groupName, keycloakInstance).flatMap {
      case Some(groupId) =>
        Task {
          val groupResource = keycloakConnector
            .getKeycloak(keycloakInstance)
            .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
            .groups()
            .group(groupId.value)
            .toRepresentation
          Right(KeycloakGroup(GroupId(groupResource.getId), GroupName(groupResource.getName)))
        }
      case None =>
        logger.error(s"Could not find group with name $groupName")
        Task(Left(GroupNotFound(groupName)))
    }

  }

  override def deleteGroup(groupName: GroupName, keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Unit] =
    getGroupIdByName(groupName, keycloakInstance).flatMap {
      case Some(groupId: GroupId) =>
        Task {
          keycloakConnector
            .getKeycloak(keycloakInstance)
            .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
            .groups()
            .group(groupId.value)
            .remove()
        }
      case None =>
        logger.error(s"Could not delete group with name $groupName as it does not exists")
        Task.unit
    }

  private def getGroupIdByName(groupName: GroupName, keycloakInstance: KeycloakInstance): Task[Option[GroupId]] =
    Task {
      keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .groups()
        .groups()
        .asScala
        .find(groupRepresentation => groupRepresentation.getName == groupName.value)
        .map(groupRepresentation => GroupId(groupRepresentation.getId))
    }
}
