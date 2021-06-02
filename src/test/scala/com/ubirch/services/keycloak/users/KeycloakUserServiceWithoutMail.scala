package com.ubirch.services.keycloak.users
import com.google.inject.Inject
import com.ubirch.models.user.UserId
import com.ubirch.services.keycloak.KeycloakRealm
import com.ubirch.services.{ KeycloakConnector, KeycloakInstance }
import monix.eval.Task
import org.keycloak.representations.idm.UserRepresentation

import scala.jdk.CollectionConverters.seqAsJavaListConverter

// Tests do not support sending emails
class KeycloakUserServiceWithoutMail @Inject() (keycloakConnector: KeycloakConnector)
  extends DefaultKeycloakUserService(keycloakConnector) {
  override def sendRequiredActionsEmail(
    realm: KeycloakRealm,
    userId: UserId,
    instance: KeycloakInstance): Task[Either[String, Unit]] = {
    getUserById(realm, userId, instance).map {
      case Some(userRepresentation: UserRepresentation) =>
        val actions = userRepresentation.getRequiredActions
        if (!actions.isEmpty) {
          userRepresentation.setRequiredActions(List.empty.asJava)
          Right(keycloakConnector.getKeycloak(instance)
            .realm(keycloakConnector.getKeycloakRealm(instance))
            .users()
            .get(userRepresentation.getId)
            .update(userRepresentation))
        } else {
          Left(s"no action is setup for user: ${userId.value}.")
        }
      case None =>
        Left(s"user with name ${userId.value} wasn't found")
    }.onErrorHandle { ex =>
      logger.error(s"failed to execute required actions to user ${userId.value}", ex)
      Left(s"failed to execute required actions to user ${userId.value}")
    }
  }
}
