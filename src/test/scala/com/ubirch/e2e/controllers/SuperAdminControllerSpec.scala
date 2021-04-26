package com.ubirch.e2e.controllers

import com.ubirch.controllers.SuperAdminController
import com.ubirch.e2e.InjectorHelperImpl
import com.ubirch.services.jwt.PublicKeyPoolService
import com.ubirch.{ Awaits, ExecutionContextsTests, FakeTokenCreator }
import io.prometheus.client.CollectorRegistry
import org.scalatest.BeforeAndAfterEach
import org.scalatra.test.scalatest.ScalatraWordSpec

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class SuperAdminControllerSpec
  extends ScalatraWordSpec
  with BeforeAndAfterEach
  with ExecutionContextsTests
  with Awaits {

  private lazy val Injector = new InjectorHelperImpl() {}

  "Super Admin Controller" must {
    "fail when token is not provided" in {
      get("/initialTest") {
        status should equal(401)
        assert(
          body == """{"version":"1.0","ok":false,"errorType":"AuthenticationError","errorMessage":"Unauthenticated"}""")
      }
    }

    "return test message when token is provided" in {
      val token = Injector.get[FakeTokenCreator]
      get("/initialTest", headers = Map("authorization" -> token.user.prepare)) {
        status should equal(200)
        assert(body == """Test successful for Carlos Sanchez""")
      }
    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
  }

  override protected def beforeAll(): Unit = {

    CollectorRegistry.defaultRegistry.clear()

    lazy val pool = Injector.get[PublicKeyPoolService]
    await(pool.init, 2 seconds)

    lazy val superAdminController = Injector.get[SuperAdminController]

    addServlet(superAdminController, "/*")

    super.beforeAll()
  }

}
