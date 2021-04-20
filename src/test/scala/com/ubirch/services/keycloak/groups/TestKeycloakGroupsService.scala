package com.ubirch.services.keycloak.groups
import com.ubirch.models.keycloak.group.{
  CreateKeycloakGroup,
  GroupAlreadyExists,
  GroupId,
  GroupName,
  GroupNotFound,
  KeycloakGroup
}
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class TestKeycloakGroupsService() extends KeycloakGroupService {
  private val groupsDatastore = mutable.Map[GroupName, KeycloakGroup]()

  override def createGroup(createKeycloakGroup: CreateKeycloakGroup): Task[Either[GroupAlreadyExists, Unit]] =
    Task {
      groupsDatastore.find(_._1 == createKeycloakGroup.groupName) match {
        case Some(_) => Left(GroupAlreadyExists(createKeycloakGroup.groupName))
        case None =>
          groupsDatastore += (
            (
              createKeycloakGroup.groupName,
              KeycloakGroup(GroupId(UUID.randomUUID().toString), createKeycloakGroup.groupName)))
          Right(())
      }
    }
  override def findGroup(groupName: GroupName): Task[Either[GroupNotFound, KeycloakGroup]] =
    Task {
      groupsDatastore.get(groupName) match {
        case Some(keycloakGroup) => Right(keycloakGroup)
        case None => Left(GroupNotFound(groupName))
      }
    }
  override def deleteGroup(groupName: GroupName): Task[Unit] =
    Task {
      groupsDatastore -= groupName
      ()
    }
}
