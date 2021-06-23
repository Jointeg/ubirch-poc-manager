package com.ubirch.e2e.controllers.assertions

import org.joda.time.{ DateTime, LocalDate }
import org.json4s.jackson.JsonMethods.parse
import org.json4s._
import org.scalatest.{ AppendedClues, Matchers }

import java.util.UUID

class PocAdminJsonAssertion(json: JValue) extends Matchers with AppendedClues { self =>
  private val validFields: Seq[String] = Seq(
    "id",
    "firstName",
    "lastName",
    "dateOfBirth",
    "email",
    "phone",
    "pocName",
    "active",
    "state",
    "webIdentRequired",
    "webIdentInitiateId",
    "webIdentSuccessId",
    "createdAt",
    "revokeTime"
  )

  {
    val parsedFields = json.asInstanceOf[JObject].obj.map { case (name, _) => name }
    parsedFields.filterNot(f =>
      validFields.contains(f)) shouldBe Seq.empty withClue "returned poc admin contains unexpected fields"
  }

  def hasId(id: UUID): PocAdminJsonAssertion = {
    json \ "id" shouldBe JString(id.toString)
    self
  }

  def hasFirstName(firstName: String): PocAdminJsonAssertion = {
    json \ "firstName" shouldBe JString(firstName)
    self
  }

  def hasLastName(lastName: String): PocAdminJsonAssertion = {
    json \ "lastName" shouldBe JString(lastName)
    self
  }

  def hasEmail(email: String): PocAdminJsonAssertion = {
    json \ "email" shouldBe JString(email)
    self
  }

  def hasActive(active: Boolean): PocAdminJsonAssertion = {
    json \ "active" shouldBe JBool(active)
    self
  }

  def hasStatus(status: String): PocAdminJsonAssertion = {
    json \ "state" shouldBe JString(status)
    self
  }

  def hasCreatedAt(createdAt: DateTime): PocAdminJsonAssertion = {
    json \ "createdAt" shouldBe JString(createdAt.toInstant.toString)
    self
  }

  def hasRevokeTime(revokeTime: DateTime): PocAdminJsonAssertion = {
    json \ "revokeTime" shouldBe JString(revokeTime.toInstant.toString)
    self
  }

  def doesNotHaveRevokeTime(): PocAdminJsonAssertion = {
    json \ "revokeTime" shouldBe JNothing
    self
  }

  def hasDateOfBirth(dateOfBirth: LocalDate): PocAdminJsonAssertion = {
    json \ "dateOfBirth" \ "year" shouldBe JInt(dateOfBirth.getYear)
    json \ "dateOfBirth" \ "month" shouldBe JInt(dateOfBirth.getMonthOfYear)
    json \ "dateOfBirth" \ "day" shouldBe JInt(dateOfBirth.getDayOfMonth)
    self
  }

  def hasPhone(phone: String): PocAdminJsonAssertion = {
    json \ "phone" shouldBe JString(phone)
    self
  }

  def hasPocName(pocName: String): PocAdminJsonAssertion = {
    json \ "pocName" shouldBe JString(pocName)
    self
  }

  def hasWebIdentRequired(webIdentRequired: Boolean): PocAdminJsonAssertion = {
    json \ "webIdentRequired" shouldBe JBool(webIdentRequired)
    self
  }

  def hasWebIdentInitiateId(webIdentInitiateId: UUID): PocAdminJsonAssertion = {
    json \ "webIdentInitiateId" shouldBe JString(webIdentInitiateId.toString)
    self
  }

  def doesNotHaveWebIdentInitiateId(): PocAdminJsonAssertion = {
    json \ "webIdentInitiateId" shouldBe JNothing
    self
  }

  def hasWebIdentSuccessId(webIdentSuccessId: String): PocAdminJsonAssertion = {
    json \ "webIdentSuccessId" shouldBe JString(webIdentSuccessId)
    self
  }

  def doesNotHaveWebIdentSuccessId(): PocAdminJsonAssertion = {
    json \ "webIdentSuccessId" shouldBe JNothing
    self
  }
}

object PocAdminJsonAssertion {
  def assertPocAdminJson(body: String): PocAdminJsonAssertion = new PocAdminJsonAssertion(parse(body))
  def assertPocAdminJson(body: JValue): PocAdminJsonAssertion = new PocAdminJsonAssertion(body)
}
