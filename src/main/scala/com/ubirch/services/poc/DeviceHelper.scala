package com.ubirch.services.poc

import com.google.inject.Inject
import com.ubirch.PocConfig
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.services.DeviceKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.PocCreator.throwError
import monix.eval.Task

trait DeviceHelper {

  def addGroupsToDevice(poc: Poc, status: PocStatus): Task[PocStatus]

}

class DeviceHelperImpl @Inject() (users: KeycloakUserService, pocConfig: PocConfig) extends DeviceHelper {

  override def addGroupsToDevice(poc: Poc, status: PocStatus): Task[PocStatus] = {

    val groupId = pocConfig.pocTypeDataSchemaMap.getOrElse(
      poc.pocType,
      throwError(
        PocAndStatus(poc, status),
        s"can't find the dataSchemaId GroupId corresponding the pocType: ${poc.pocType}"))

    val status1 =
      addGroupByIdToDevice(groupId, PocAndStatus(poc, status)).map {
        case Right(_) =>
          status.copy(assignedDataSchemaGroup = true)
        case Left(errorMsg) =>
          throwError(PocAndStatus(poc, status), s"failed to add data schema group $groupId to device: $errorMsg")
      }

    val trustedPocGroupId = pocConfig.pocTypeTrustedPocMap.getOrElse(
      poc.pocType,
      throwError(
        PocAndStatus(poc, status),
        s"can't find the trustedPocGroupId corresponding the pocType: ${poc.pocType}"))

    val status2 = status1.flatMap { status =>
      addGroupByIdToDevice(trustedPocGroupId, PocAndStatus(poc, status)).map {
        case Right(_) => status.copy(assignedTrustedPocGroup = true)
        case Left(errorMsg) =>
          throwError(
            PocAndStatus(poc, status),
            s"failed to add trusted poc group $trustedPocGroupId to device $errorMsg")
      }
    }

    for {
      status <- status2
      pocDeviceGroup = poc.deviceGroupId.getOrElse(throwError(
        PocAndStatus(poc, status),
        "pocDeviceGroupId is missing, when it should be added to device"))
      success <- addGroupByIdToDevice(pocDeviceGroup, PocAndStatus(poc, status))
    } yield {
      success match {
        case Right(_) => status.copy(assignedDeviceGroup = true)
        case Left(errorMsg) =>
          throwError(PocAndStatus(poc, status), s"failed to add poc group $pocDeviceGroup to device $errorMsg")
      }
    }
  }

  private def addGroupByIdToDevice(groupId: String, pocAndStatus: PocAndStatus): Task[Either[String, Unit]] = {
    val deviceId = pocAndStatus.poc.getDeviceId
    users.addGroupToUserByName(deviceId, groupId, DeviceKeycloak)
  }

}
