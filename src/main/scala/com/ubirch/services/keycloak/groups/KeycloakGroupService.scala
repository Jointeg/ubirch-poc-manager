package com.ubirch.services.keycloak.groups

import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.group._
import com.ubirch.services.keycloak.{ KeycloakConfig, KeycloakConnector }
import monix.eval.Task

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

trait KeycloakGroupService {

  def createGroup(createKeycloakGroup: CreateKeycloakGroup): Task[Either[GroupAlreadyExists, Unit]]
  def findGroup(groupName: GroupName): Task[Either[GroupNotFound, KeycloakGroup]]
  def deleteGroup(groupName: GroupName): Task[Unit]

}

class DefaultKeycloakGroupService @Inject() (keycloakConnector: KeycloakConnector, keycloakConfig: KeycloakConfig)
  extends KeycloakGroupService
  with LazyLogging {
  override def createGroup(createKeycloakGroup: CreateKeycloakGroup): Task[Either[GroupAlreadyExists, Unit]] =
    Task {
      val response = keycloakConnector.keycloak
        .realm(keycloakConfig.usersRealm)
        .groups()
        .add(createKeycloakGroup.toKeycloakRepresentation)

      if (response.getStatus == 409) {
        logger.error(s"Could not create group with name ${createKeycloakGroup.groupName} as it already exists")
        Left(GroupAlreadyExists(createKeycloakGroup.groupName))
      } else {
        Right(())
      }
    }

  override def findGroup(groupName: GroupName): Task[Either[GroupNotFound, KeycloakGroup]] = {
    getGroupIdByName(groupName).flatMap {
      case Some(groupId) =>
        Task {
          val groupResource = keycloakConnector.keycloak
            .realm(keycloakConfig.usersRealm)
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

  override def deleteGroup(groupName: GroupName): Task[Unit] =
    getGroupIdByName(groupName).flatMap {
      case Some(groupId: GroupId) =>
        Task {
          keycloakConnector.keycloak
            .realm(keycloakConfig.usersRealm)
            .groups()
            .group(groupId.value)
            .remove()
        }
      case None =>
        logger.error(s"Could not delete group with name $groupName as it does not exists")
        Task.unit
    }

  private def getGroupIdByName(groupName: GroupName): Task[Option[GroupId]] =
    Task {
      keycloakConnector.keycloak
        .realm(keycloakConfig.usersRealm)
        .groups()
        .groups()
        .asScala
        .find(groupRepresentation => groupRepresentation.getName == groupName.value)
        .map(groupRepresentation => GroupId(groupRepresentation.getId))
    }
}
