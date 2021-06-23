package com.ubirch.test

import com.ubirch.models.poc.{ BirthDate, Pending, Status }
import com.ubirch.models.tenant.TenantName
import org.joda.time.LocalDate

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
  }

  object Poc {
    val pocTypeUbVacApp: String = "ub_vac_app"
    val pocTypeUbVacApi: String = "ub_vac_api"
    val pocTypeBmgVacApi: String = "bmg_vac_api"
  }
}
