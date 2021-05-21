package com.ubirch.services.poc

import com.google.inject.Inject
import com.ubirch.PocConfig
import com.ubirch.models.keycloak.group.{ GroupId, GroupName, GroupNotFound }
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.models.user.UserName
import com.ubirch.services.DeviceKeycloak
import com.ubirch.services.keycloak.groups.KeycloakGroupService
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.PocCreator.throwError
import monix.eval.Task

trait DeviceHelper {

  def addGroupsToDevice(poc: Poc, status: PocStatus): Task[PocStatus]

}

class DeviceHelperImpl @Inject() (users: KeycloakUserService, pocConfig: PocConfig) extends DeviceHelper {

  override def addGroupsToDevice(poc: Poc, status: PocStatus): Task[PocStatus] = {

    val groupId = pocConfig.dataSchemaGroupMap.getOrElse(
      poc.dataSchemaId,
      throwError(PocAndStatus(poc, status), s"can't find the uuid corresponding the dataSchemaId: ${poc.dataSchemaId}"))

    val status1 =
      addGroupByIdToDevice(groupId, PocAndStatus(poc, status))
        .map {
          case Right(_) =>
            status.copy(assignedDataSchemaGroup = true)
          case Left(errorMsg) =>
            throwError(PocAndStatus(poc, status), s"failed to add device to group $groupId: $errorMsg")
        }

    for {
      status2 <- status1
      pocDeviceGroup = poc.deviceGroupId.getOrElse(throwError(
        PocAndStatus(poc, status2),
        "pocDeviceGroupId is missing, when it should be added to device"))
      success <- addGroupByIdToDevice(pocDeviceGroup, PocAndStatus(poc, status2))
    } yield {
      success match {
        case Right(_) => status2.copy(assignedDeviceGroup = true)
        case Left(errorMsg) =>
          throwError(PocAndStatus(poc, status2), s"failed to add group $pocDeviceGroup to device $errorMsg")
      }
    }
  }

  private def addGroupByIdToDevice(groupId: String, pocAndStatus: PocAndStatus): Task[Either[String, Unit]] = {
    val deviceId = pocAndStatus.poc.getDeviceId
    users.addGroupToUserByName(deviceId, groupId, DeviceKeycloak)
  }

}
