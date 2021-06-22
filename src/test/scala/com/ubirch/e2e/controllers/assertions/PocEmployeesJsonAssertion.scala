package com.ubirch.e2e.controllers.assertions

import com.ubirch.models.pocEmployee.PocEmployee
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.Matchers

class PocEmployeesJsonAssertion(json: JValue) extends Matchers { self =>
  def hasTotal(total: Int): PocEmployeesJsonAssertion = {
    json \ "total" shouldBe JInt(total)
    self
  }

  def hasEmployeeCount(count: Int): PocEmployeesJsonAssertion = {
    (json \ "records").values.asInstanceOf[Seq[_]].size shouldBe count
    self
  }

  def hasEmployeeAtIndex(index: Int)(assert: PocEmployeeJsonAssertion => Unit): PocEmployeesJsonAssertion = {
    assert(PocEmployeeJsonAssertion.assertPocEmployeeJson((json \ "records")(index)))
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
