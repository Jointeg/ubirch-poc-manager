package com.ubirch.services.poc

import com.google.inject.Inject
import com.ubirch.PocConfig
import com.ubirch.models.poc.{ Poc, PocStatus }
import com.ubirch.services.DeviceKeycloak
import com.ubirch.services.keycloak.users.KeycloakUserService
import com.ubirch.services.poc.PocCreator.throwError
import monix.eval.Task
import cats.syntax.traverse._
import cats.syntax.either._

trait DeviceHelper {

  def addGroupsToDevice(poc: Poc, status: PocStatus): Task[PocStatus]

}

class DeviceHelperImpl @Inject() (users: KeycloakUserService, pocConfig: PocConfig) extends DeviceHelper {

  override def addGroupsToDevice(poc: Poc, status: PocStatus): Task[PocStatus] = {

    val schemaAndTrustedGroupTuple: Seq[(String, String)] = getDataSchemaAndTrustedPocGroupId(poc, status)

    val status1 = schemaAndTrustedGroupTuple.toList.traverse {
      case (dataSchemaGroupId, _) =>
        addGroupByIdToDevice(dataSchemaGroupId, PocAndStatus(poc, status)).map {
          _.leftMap(errorMsg =>
            throwError(
              PocAndStatus(poc, status),
              s"failed to add data schema group with id $dataSchemaGroupId to device: $errorMsg"))
        }
    }.map(_ => status.copy(assignedDataSchemaGroup = true))

    val status2 = status1.flatMap { status =>
      schemaAndTrustedGroupTuple.toList.traverse {
        case (_, trustedPocGroupId) =>
          addGroupByIdToDevice(trustedPocGroupId, PocAndStatus(poc, status)).map {
            _.leftMap(errorMsg =>
              throwError(
                PocAndStatus(poc, status),
                s"failed to add data schema group with id $trustedPocGroupId to device: $errorMsg"))
          }
      }.map(_ => status.copy(assignedTrustedPocGroup = true))
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

  @throws[PocCreationError]
  private def getDataSchemaAndTrustedPocGroupId(poc: Poc, status: PocStatus): Seq[(String, String)] = {
    val dataSchemaIds = pocConfig.pocTypeDataSchemaMap.getOrElse(
      poc.pocType,
      throwError(
        PocAndStatus(poc, status),
        s"can't find pocType ${poc.pocType} in pocTypeDataSchemaMap: ${poc.pocType}"))

    dataSchemaIds.map { dataSchemaId =>

      val dataSchemaGroupId = pocConfig.dataSchemaGroupMap.getOrElse(
        dataSchemaId,
        throwError(
          PocAndStatus(poc, status),
          s"can't find the dataSchemaId $dataSchemaId in dataSchemaGroupMap"))

      val trustedPocGroupId = pocConfig.trustedPocGroupMap.getOrElse(
        dataSchemaId,
        throwError(
          PocAndStatus(poc, status),
          s"can't find the dataSchemaId $dataSchemaId in trustedPocGroupMap"))

      (dataSchemaGroupId, trustedPocGroupId)
    }
  }

  private def addGroupByIdToDevice(groupId: String, pocAndStatus: PocAndStatus): Task[Either[String, Unit]] = {
    val deviceId = pocAndStatus.poc.getDeviceId
    users.addGroupToUserByName(DeviceKeycloak.defaultRealm, deviceId, groupId, DeviceKeycloak)
  }

}
