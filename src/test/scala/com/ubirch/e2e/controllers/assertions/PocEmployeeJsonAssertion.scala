package com.ubirch.e2e.controllers.assertions

import org.joda.time.{DateTime, Instant}
import org.json4s.{JBool, JString, JValue}
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.Matchers

import java.util.UUID

class PocEmployeeJsonAssertion(json: JValue) extends Matchers { self =>
  //      "firstName": "${pe.name}",
  //      "lastName": "${pe.surname}",
  //      "email": "${pe.email}",
  //      "active": ${pe.active},
  //      "status": "${Status.toFormattedString(pe.status)}",
  //      "createdAt": "${pe.created.dateTime.toInstant}"
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
}

object PocEmployeeJsonAssertion {
  def assertPocEmployeeJson(body: String): PocEmployeeJsonAssertion = new PocEmployeeJsonAssertion(parse(body))
  def assertPocEmployeeJson(body: JValue): PocEmployeeJsonAssertion = new PocEmployeeJsonAssertion(body)
}
