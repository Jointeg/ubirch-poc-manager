package com.ubirch.e2e.controllers.assertions

import org.joda.time.DateTime
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{ JBool, JNothing, JObject, JString, JValue }
import org.scalatest.{ AppendedClues, Matchers }

import java.util.UUID

class PocEmployeeJsonAssertion(json: JValue) extends Matchers with AppendedClues { self =>
  private val validFields: Seq[String] = Seq(
    "id",
    "firstName",
    "lastName",
    "email",
    "active",
    "status",
    "revokeTime",
    "createdAt"
  )

  {
    val parsedFields = json.asInstanceOf[JObject].obj.map { case (name, _) => name }
    parsedFields.filterNot(f =>
      validFields.contains(f)) shouldBe Seq.empty withClue "returned poc employee's fields did not match expected ones"
  }

  def hasId(id: UUID): PocEmployeeJsonAssertion = {
    json \ "id" shouldBe JString(id.toString)
    self
  }
  def hasFirstName(firstName: String): PocEmployeeJsonAssertion = {
    json \ "firstName" shouldBe JString(firstName)
    self
  }

  def hasLastName(lastName: String): PocEmployeeJsonAssertion = {
    json \ "lastName" shouldBe JString(lastName)
    self
  }

  def hasEmail(email: String): PocEmployeeJsonAssertion = {
    json \ "email" shouldBe JString(email)
    self
  }

  def hasActive(active: Boolean): PocEmployeeJsonAssertion = {
    json \ "active" shouldBe JBool(active)
    self
  }

  def hasStatus(status: String): PocEmployeeJsonAssertion = {
    json \ "status" shouldBe JString(status)
    self
  }

  def hasCreatedAt(createdAt: DateTime): PocEmployeeJsonAssertion = {
    json \ "createdAt" shouldBe JString(createdAt.toInstant.toString)
    self
  }

  def hasRevokeTime(removeTime: DateTime): PocEmployeeJsonAssertion = {
    json \ "revokeTime" shouldBe JString(removeTime.toInstant.toString)
    self
  }

  def doesNotHaveRevokeTime(): PocEmployeeJsonAssertion = {
    json \ "revokeTime" shouldBe JNothing
    self
  }
}

object PocEmployeeJsonAssertion {
  def assertPocEmployeeJson(body: String): PocEmployeeJsonAssertion = new PocEmployeeJsonAssertion(parse(body))
  def assertPocEmployeeJson(body: JValue): PocEmployeeJsonAssertion = new PocEmployeeJsonAssertion(body)
}
