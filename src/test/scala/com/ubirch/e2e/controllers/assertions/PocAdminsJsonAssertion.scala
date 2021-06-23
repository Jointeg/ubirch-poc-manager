package com.ubirch.e2e.controllers.assertions

import com.ubirch.models.poc.{ Poc, PocAdmin }
import org.json4s._
import org.json4s.jackson.JsonMethods.parse
import org.scalatest.Matchers

class PocAdminsJsonAssertion(json: JValue) extends Matchers { self =>
  def hasTotal(total: Int): PocAdminsJsonAssertion = {
    json \ "total" shouldBe JInt(total)
    self
  }

  def hasAdminCount(count: Int): PocAdminsJsonAssertion = {
    (json \ "records").values.asInstanceOf[Seq[_]].size shouldBe count
    self
  }

  def hasAdmins(es: Seq[(Poc, PocAdmin)]): PocAdminsJsonAssertion = {
    es.zipWithIndex.foreach {
      case ((poc, admin), i) => hasAdminAtIndex(i, poc, admin)
    }
    self
  }

  def hasAdminAtIndex(index: Int)(assert: PocAdminJsonAssertion => Unit): PocAdminsJsonAssertion = {
    assert(PocAdminJsonAssertion.assertPocAdminJson((json \ "records")(index)))
    self
  }

  def hasAdminAtIndex(index: Int, poc: Poc, admin: PocAdmin): PocAdminsJsonAssertion = {
    hasAdminAtIndex(index)(
      _.hasId(admin.id)
        .hasFirstName(admin.name)
        .hasLastName(admin.surname)
        .hasPocName(poc.pocName)
        .hasEmail(admin.email)
        .hasPhone(admin.mobilePhone)
        .hasDateOfBirth(admin.dateOfBirth.date)
        .hasActive(admin.active)
        .hasStatus(admin.status.toString.toUpperCase)
        .hasCreatedAt(admin.created.dateTime)
//        .hasRevokeTime(admin.webAuthnDisconnected)
//        .hasWebIdentSuccessId(admin.webIdentId)
//        .hasWebIdentInitiateId(admin.webIdentInitiateId)
        .hasWebIdentRequired(admin.webIdentRequired)
    )
    self
  }
}

object PocAdminsJsonAssertion {
  def assertPocAdminsJson(body: String): PocAdminsJsonAssertion = new PocAdminsJsonAssertion(parse(body))
}
