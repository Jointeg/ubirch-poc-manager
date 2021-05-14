package com.ubirch.services.keycloak.groups

import com.ubirch.ModelCreationHelper.dataSchemaGroupId
import com.ubirch.models.keycloak.group._
import com.ubirch.services.{ CertifyKeycloak, DeviceKeycloak, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.{ GroupRepresentation, RoleRepresentation, UserRepresentation }

import javax.inject.Singleton
import scala.collection.mutable
import scala.jdk.CollectionConverters.{ collectionAsScalaIterableConverter, seqAsJavaListConverter }

@Singleton
class TestKeycloakGroupsService() extends KeycloakGroupService {

  private val groupsCertifyDatastore = mutable.Map[String, GroupRepresentation]()
  private val groupsDeviceDatastore = mutable.Map[String, GroupRepresentation]()
  private val dataSchemaGroup = createGroupRepresentation(GroupName(dataSchemaGroupId))
  groupsDeviceDatastore += ((dataSchemaGroupId, dataSchemaGroup))

  override def createGroup(
    createKeycloakGroup: CreateKeycloakGroup,
    keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Either[GroupCreationError, GroupId]] =
    keycloakInstance match {
      case CertifyKeycloak => insertIfNotExists(groupsCertifyDatastore, createKeycloakGroup)
      case DeviceKeycloak  => insertIfNotExists(groupsDeviceDatastore, createKeycloakGroup)
    }

  private def insertIfNotExists(
    datastore: mutable.Map[String, GroupRepresentation],
    createKeycloakGroup: CreateKeycloakGroup): Task[Either[GroupCreationError, GroupId]] = {
    Task {
      datastore.find(_._2.getName == createKeycloakGroup.groupName.value) match {
        case Some(_) => Left(GroupCreationError(createKeycloakGroup.groupName.value))
        case None =>
          val group = createGroupRepresentation(createKeycloakGroup.groupName)
          datastore += ((group.getId, group))
          Right(GroupId(group.getId))
      }
    }
  }

  override def findGroupByName(
    groupName: GroupName,
    instance: KeycloakInstance = CertifyKeycloak): Task[Either[GroupNotFound, GroupRepresentation]] =
    instance match {
      case CertifyKeycloak => findInDatastore(groupsCertifyDatastore, groupName)
      case DeviceKeycloak  => findInDatastore(groupsDeviceDatastore, groupName)
    }

  private def findInDatastore(datastore: mutable.Map[String, GroupRepresentation], groupName: GroupName) = {
    Task {
      datastore.find(_._2.getName == groupName.value) match {
        case Some(keycloakGroup) => Right(keycloakGroup._2)
        case None                => Left(GroupNotFound(groupName))
      }
    }
  }

  override def findGroupById(groupId: GroupId, instance: KeycloakInstance): Task[Either[String, GroupRepresentation]] =
    Task(instance match {
      case CertifyKeycloak => findIdInDatastore(groupsCertifyDatastore, groupId)
      case DeviceKeycloak  => findIdInDatastore(groupsDeviceDatastore, groupId)
    })

  private def findIdInDatastore(
    datastore: mutable.Map[String, GroupRepresentation],
    groupId: GroupId): Either[String, GroupRepresentation] = {
    datastore.get(groupId.value) match {
      case Some(group) => Right(group)
      case None        => Left(s"failed to find group by id ${groupId.value}")
    }
  }

  override def deleteGroup(groupName: GroupName, keycloakInstance: KeycloakInstance = CertifyKeycloak): Task[Unit] =
    keycloakInstance match {
      case CertifyKeycloak =>
        Task(groupsCertifyDatastore -= groupName.value)
      case DeviceKeycloak =>
        Task(groupsDeviceDatastore -= groupName.value)
    }

  override def addSubGroup(
    parentGroupId: GroupId,
    childGroupName: GroupName,
    instance: KeycloakInstance): Task[Either[GroupCreationError, GroupId]] = {

    val childGroup = createGroupRepresentation(childGroupName)

    val r = instance match {
      case CertifyKeycloak =>
        findIdInDatastore(groupsCertifyDatastore, parentGroupId)
      case DeviceKeycloak =>
        findIdInDatastore(groupsDeviceDatastore, parentGroupId)
    }
    r match {
      case Right(group) =>
        group.setSubGroups(List(childGroup).asJava)
        instance match {
          case CertifyKeycloak =>
            groupsCertifyDatastore += ((childGroup.getId, childGroup))
          case DeviceKeycloak =>
            groupsDeviceDatastore += ((childGroup.getId, childGroup))
        }
        Task(Right(GroupId(childGroup.getId)))
      case _ =>
        Task(Left(GroupCreationError("couldn't find parentGroup")))
    }
  }

  override def addRoleToGroup(
    groupId: GroupId,
    role: RoleRepresentation,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {

    val r = instance match {
      case CertifyKeycloak =>
        findChildGroupInDatastore(groupsCertifyDatastore, groupId)
      case DeviceKeycloak =>
        findChildGroupInDatastore(groupsDeviceDatastore, groupId)
    }
    Task(r match {
      case Right(group) =>
        if (group.getRealmRoles == null)
          group.setRealmRoles(List(role.getName).asJava)
        else
          group.getRealmRoles.add(role.getName)
        Right((): Unit)
      case _ =>
        Left("role adding error")
    })
  }

  def addMemberToGroup(
    groupId: GroupId,
    user: UserRepresentation,
    instance: KeycloakInstance = CertifyKeycloak): Task[Either[String, Boolean]] = {
    Task(Right(true))
  }

  private def findChildGroupInDatastore(
    datastore: mutable.Map[String, GroupRepresentation],
    groupId: GroupId): Either[String, GroupRepresentation] = {

    val r = datastore.flatMap(_._2.getSubGroups.asScala)
    r.find(_.getId == groupId.value) match {
      case Some(group) =>
        Right(group)
      case None =>
        Left(s"failed to find group by id $groupId")
    }
  }

  private def createGroupRepresentation(
    name: GroupName,
    id: String = "c618b7cf-d798-49c8-adb1-c91a122904f6"): GroupRepresentation = {
    val group = new GroupRepresentation()
    group.setId(id)
    group.setName(name.value)
    group.setSubGroups(List(group).asJava)
    group
  }
}
