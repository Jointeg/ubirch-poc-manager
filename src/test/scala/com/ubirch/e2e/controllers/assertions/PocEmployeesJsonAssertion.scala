package com.ubirch.e2e.controllers.assertions

import com.ubirch.models.pocEmployee.PocEmployee
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{AppendedClues, Matchers}

class PocEmployeesJsonAssertion(json: JValue) extends Matchers with AppendedClues { self =>
  private val expectedFields: Seq[String] = Seq(
    "id",
    "firstName",
    "lastName",
    "email",
    "active",
    "status",
    "createdAt"
  )

  def hasTotal(total: Int): PocEmployeesJsonAssertion = {
    json \ "total" shouldBe JInt(total)
    self
  }

  def hasEmployeeCount(count: Int): PocEmployeesJsonAssertion = {
    (json \ "records").values.asInstanceOf[Seq[_]].size shouldBe count
    self
  }

  def hasEmployees(es: Seq[PocEmployee]): PocEmployeesJsonAssertion = {
    es.zipWithIndex.foreach {
      case (employee, i) => hasEmployeeAtIndex(i, employee)
    }
    self
  }

  def hasEmployeeAtIndex(index: Int)(assert: PocEmployeeJsonAssertion => Unit): PocEmployeesJsonAssertion = {
    val jValue = (json \ "records") (index)
    val parsedFields = jValue.asInstanceOf[JObject].obj.map(_._1)
    parsedFields shouldBe expectedFields withClue "returned poc employee's fields did not match expected ones"
    assert(PocEmployeeJsonAssertion.assertPocEmployeeJson(jValue))
    self
  }

  def hasEmployeeAtIndex(index: Int, employee: PocEmployee): PocEmployeesJsonAssertion = {
    hasEmployeeAtIndex(index)(
      _.hasId(employee.id)
        .hasFirstName(employee.name)
        .hasLastName(employee.surname)
        .hasEmail(employee.email)
        .hasActive(employee.active)
        .hasStatus(employee.status.toString.toUpperCase)
        .hasCreatedAt(employee.created.dateTime)
    )
    self
  }
}

object PocEmployeesJsonAssertion {
  def assertPocEmployeesJson(body: String): PocEmployeesJsonAssertion = new PocEmployeesJsonAssertion(parse(body))
}
