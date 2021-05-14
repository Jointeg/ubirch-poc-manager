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

  /**
    * Method tries to add group; if 409 Conflict is returned,
    * the id of the group is being tried to retrieve.
    */
  override def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    instance: KeycloakInstance = UsersKeycloak): Task[Either[GroupCreationError, GroupId]] = {

    val groupName = createKeycloakGroup.groupName.value
    Task {
      val group = createKeycloakGroup.toKeycloakRepresentation
      val response = keycloakConnector
        .getKeycloak(instance)
        .realm(keycloakConnector.getKeycloakRealm(instance))
        .groups()
        .add(group)

      processCreationResponse(response, GroupName(group.getName))
    }.flatMap {
      case Left(GroupAlreadyExists(groupName)) =>
        getGroupIdByName(groupName, instance).map {
          case Some(id) => Right(id)
          case None     => Left(GroupCreationError(s"couldn't retrieve group $groupName despite 409 Conflict was returned"))
        }
      case Left(error: GroupCreationError) => Task(Left(error))
      case Right(groupId)                  => Task(Right(groupId))
    }.onErrorHandle(handleGroupCreationException(groupName))
  }

  override def findGroupById(
    groupId: GroupId,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[String, GroupRepresentation]] = {
    Task(
      Right(keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .groups()
        .group(groupId.value)
        .toRepresentation)
    ).onErrorHandle { ex =>
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
        Task(
          Right(keycloakConnector
            .getKeycloak(keycloakInstance)
            .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
            .groups()
            .group(groupId.value)
            .toRepresentation))
      case None =>
        Task(Left(GroupNotFound(groupName)))
    }.onErrorHandle { ex =>
      val errorMsg = s"finding group by name ${groupName.value} failed due to, ${ex.getMessage}"
      logger.error(errorMsg, ex)
      Left(GroupNotFound(groupName))
    }
  }

  /**
    * Method tries to add sub group; if 409 Conflict is returned,
    * the id of the child group is being tried to retrieve.
    */
  override def addSubGroup(
    parentGroupId: GroupId,
    childGroupName: GroupName,
    instance: KeycloakInstance = UsersKeycloak): Task[Either[GroupCreationError, GroupId]] = {

    Task {
      val group = CreateKeycloakGroup(childGroupName).toKeycloakRepresentation
      val response = keycloakConnector
        .getKeycloak(instance)
        .realm(keycloakConnector.getKeycloakRealm(instance))
        .groups()
        .group(parentGroupId.value)
        .subGroup(group)
      processCreationResponse(response, childGroupName)
    }.flatMap {
      case Left(GroupAlreadyExists(groupName)) =>
        findChildGroup(groupName, parentGroupId, instance).map {
          case Right(groupId) => Right(groupId)
          case Left(GroupNotFound(groupName)) =>
            Left(GroupCreationError(s"couldn't find child group with name ${groupName.value}"))
        }
      case Left(GroupCreationError(errorMsg)) => Task(Left(GroupCreationError(errorMsg)))
      case Right(groupId)                     => Task(Right(groupId))
    }.onErrorHandle(handleGroupCreationException(childGroupName.value))
  }

  private def findChildGroup(
    groupName: GroupName,
    parentGroupId: GroupId,
    instance: KeycloakInstance): Task[Either[GroupNotFound, GroupId]] = {

    findGroupById(parentGroupId, instance).map {
      case Right(groupResource: GroupRepresentation) =>
        groupResource
          .getSubGroups
          .asScala
          .find(g => g.getName.equals(groupName.value)) match {
          case Some(childGroup) => Right(GroupId(childGroup.getId))
          case None             => Left(GroupNotFound(groupName))
        }
      case Left(_) => Left(GroupNotFound(groupName))
    }.onErrorHandle { ex =>
      logger.error(s"finding group by name ${groupName.value} failed", ex)
      Left(GroupNotFound(groupName))
    }
  }

  override def addRoleToGroup(
    groupId: GroupId,
    role: RoleRepresentation,
    keycloakInstance: KeycloakInstance = UsersKeycloak): Task[Either[String, Unit]] = {

    Task(
      Right(keycloakConnector
        .getKeycloak(keycloakInstance)
        .realm(keycloakConnector.getKeycloakRealm(keycloakInstance))
        .groups()
        .group(groupId.value)
        .roles()
        .realmLevel()
        .add(List(role).asJava)))
      .onErrorHandle { ex: Throwable =>
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

  private def processCreationResponse(
    response: Response,
    groupName: GroupName): Either[GroupCreationException, GroupId] = {

    if (response.getStatusInfo.equals(Status.CREATED)) {
      Right(getIdFromPath(response))
    } else if (response.getStatusInfo.equals(Status.CONFLICT)) {
      logger.info(s"group with name ${groupName.value} already existed")
      Left(GroupAlreadyExists(groupName))
    } else {
      Left(GroupCreationError(s"failed to create group ${groupName.value}; response has status ${response.getStatus}"))
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

  private def handleGroupCreationException(groupName: String): Throwable => Left[GroupCreationError, Nothing] = {
    ex: Throwable =>
      val errorMsg = s"creation of group $groupName failed ${ex.getMessage}"
      logger.error(errorMsg, ex)
      Left(GroupCreationError(errorMsg))
  }

}
