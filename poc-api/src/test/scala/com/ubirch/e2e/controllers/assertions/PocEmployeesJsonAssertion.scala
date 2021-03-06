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

  def hasEmployees(es: Seq[PocEmployee]): PocEmployeesJsonAssertion = {
    es.zipWithIndex.foreach {
      case (employee, i) => hasEmployeeAtIndex(i, employee)
    }
    self
  }

  def hasEmployeeAtIndex(index: Int)(assert: PocEmployeeJsonAssertion => Unit): PocEmployeesJsonAssertion = {
    assert(PocEmployeeJsonAssertion.assertPocEmployeeJson((json \ "records")(index)))
    self
  }

  def hasEmployeeAtIndex(index: Int, employee: PocEmployee): PocEmployeesJsonAssertion = {
    hasEmployeeAtIndex(index) { assertion =>
      assertion.hasId(employee.id)
        .hasFirstName(employee.name)
        .hasLastName(employee.surname)
        .hasEmail(employee.email)
        .hasActive(employee.active)
        .hasStatus(employee.status.toString.toUpperCase)
        .hasCreatedAt(employee.created.dateTime)

      employee.webAuthnDisconnected match {
        case Some(revokeTime) => assertion.hasRevokeTime(revokeTime)
        case None             => assertion.doesNotHaveRevokeTime()
      }
    }
    self
  }
}

object PocEmployeesJsonAssertion {
  def assertPocEmployeesJson(body: String): PocEmployeesJsonAssertion = new PocEmployeesJsonAssertion(parse(body))
}
