package com.ubirch.services.keycloak.groups
import com.google.inject.Inject
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.keycloak.group._
import com.ubirch.services.{ KeycloakConnector, KeycloakInstance, UsersKeycloak }
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation }

import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status
import scala.jdk.CollectionConverters.{ collectionAsScalaIterableConverter, seqAsJavaListConverter }

class DefaultKeycloakGroupService @Inject() (keycloakConnector: KeycloakConnector)
  extends KeycloakGroupService
  with LazyLogging {

  override def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupException, GroupId]] = {

    Task {
      val group = createKeycloakGroup.toKeycloakRepresentation
      val response = keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .groups()
        .add(group)

      processCreationResponse(response, GroupName(group.getName))
    }
  }

  override def findGroupById(
    groupId: GroupId,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[String, GroupRepresentation]] = {
    Task {
      val groupResource = keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .groups()
        .group(groupId.value)
        .toRepresentation
      Right(groupResource)
    }.onErrorHandle { ex =>
      val errorMsg = s"finding group by id $groupId failed due to, ${ex.getMessage}"
      logger.error(errorMsg, ex)
      Left(errorMsg)
    }
  }

  override def findGroupByName(
    groupName: GroupName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupNotFound, GroupRepresentation]] = {
    getGroupIdByName(groupName, keycloakInstance).flatMap {
      case Some(groupId) =>
        Task {
          val groupResource = keycloakConnector
            .getKeycloak(keycloakInstance)
            .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
            .groups()
            .group(groupId.value)
            .toRepresentation
          Right(groupResource)
        }
      case None =>
        Task(Left(GroupNotFound(groupName)))
    }
  }

  override def addSubGroup(
    parentGroupId: GroupId,
    childGroupName: GroupName,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[GroupException, GroupId]] = {
    Task {
      val group = CreateKeycloakGroup(childGroupName).toKeycloakRepresentation
      val response = keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .groups()
        .group(parentGroupId.value)
        .subGroup(group)
      processCreationResponse(response, childGroupName)
    }.onErrorHandle { ex: Throwable =>
      val errorMsg = s"creation of group $childGroupName failed ${ex.getMessage}"
      logger.error(errorMsg, ex)
      Left(GroupCreationError(errorMsg))
    }
  }

  override def addRoleToGroup(
    groupId: GroupId,
    role: RoleRepresentation,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[String, Unit]] = {

    Task {
      Right(keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .groups()
        .group(groupId.value)
        .roles()
        .realmLevel()
        .add(List(role).asJava))
    }.onErrorHandle { ex: Throwable =>
      val errorMsg = s"adding role ${role.getName} to group with id $groupId failed, due to ${ex.getMessage}"
      logger.error(errorMsg, ex)
      Left(errorMsg)
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

  private def processCreationResponse(response: Response, groupName: GroupName): Either[GroupException, GroupId] = {

    if (response.getStatus == 409) {
      logger.error(s"could not create group $groupName as it already exists")
      Left(GroupAlreadyExists(groupName))
    } else if (response.getStatusInfo.equals(Status.CREATED)) {
      Right(getIdFromPath(response))
    } else {
      Left(GroupCreationError(s"failed to create group $groupName; response has status ${response.getStatus}"))
    }
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

  private def getIdFromPath(response: Response) = {
    val path = response.getLocation.getPath
    GroupId(path.substring(path.lastIndexOf('/') + 1))
  }

}
