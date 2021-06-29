package com.ubirch.test

import com.ubirch.models.poc.{ BirthDate, Pending, Status }
import com.ubirch.models.tenant.TenantName
import org.joda.time.LocalDate

import java.util.UUID

object TestData {
  val username: String = "username"
  val password: String = "password"
  val spaceName: String = "space-name"
  val spacePath: String = "space/path"
  val email: String = "email@example.com"
  val email2: String = "email2@example.com"
  val defaultPermissionLevel: String = "readWrite"
  val spaceId: Int = 2
  val tenantName: TenantName = TenantName("tenantName")

  object PocAdmin {
    val name: String = "poc"
    val surname: String = "admin"
    val email: String = "poc.admin@ubirch.com"
    val mobilePhone: String = "123456789"
    val webIdentRequired: Boolean = true
    val webIdentIdentifier: Option[Boolean] = None
    val dateOfBirth: BirthDate = BirthDate(LocalDate.now())
    val status: Status = Pending
    val webIdentInitiateId: UUID = UUID.fromString("278a3f6d-6c90-4f52-b65e-f50d955691c2")
    val webIdentId: UUID = UUID.fromString("85740cd7-d1f0-4eae-b2b5-5bb5096deaf8")
    val certifyUserId: UUID = UUID.fromString("49815d2d-5f31-4034-9e75-2600d36bf01c")
  }

  object PocEmployee {
    val id: UUID = UUID.fromString("6ca21675-989b-4ff7-9084-74c528bb4467")
    val certifyUserId: UUID = UUID.fromString("26191c63-3edc-4b11-8933-ba265a993528")
  }

  object Poc {
    val id: UUID = UUID.fromString("08089989-4149-4642-baaf-b094b64dfbe9")
    val pocTypeUbVacApp: String = "ub_vac_app"
    val pocTypeUbVacApi: String = "ub_vac_api"
    val pocTypeBmgVacApi: String = "bmg_vac_api"
  }
}
