package com.ubirch.services.poc

import com.google.inject.AbstractModule
import com.google.inject.binder.ScopedBindingBuilder
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.models.NamespacedUUID
import com.ubirch.models.poc.{ DeviceId, Poc, PocStatus }
import com.ubirch.{ Awaits, DefaultUnitTestBinder, InjectorHelper }
import monix.execution.Scheduler
import org.scalatra.test.scalatest.ScalatraWordSpec

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Try

class InformationProviderTest extends ScalatraWordSpec with Awaits {

  def testInjector(binder: AbstractModule): InjectorHelper = new InjectorHelper(List(binder)) {}
  implicit private val scheduler: Scheduler = monix.execution.Scheduler.global
  private val javaUUID = UUID.fromString("114a0692-2728-4528-8f54-88fb4f24d94a")
  private val namespacedUUID: NamespacedUUID = NamespacedUUID.fromJavaUUID(javaUUID)
  private val deviceId = DeviceId(namespacedUUID)
  private val pw = "24b98904-b03a-48cc-a779-73630add12ef"
  private val tenant = createTenant()
  private val poc: Poc = createPoc(tenantName = tenant.tenantName).copy(deviceId = deviceId)
  private val pocStatus: PocStatus = createPocStatus(poc.id)
  private val statusAndPW = StatusAndPW(pocStatus, pw)

  "InformationProvider" should {

    class SuccessUnitTestBinder extends DefaultUnitTestBinder {
      override def InformationProvider: ScopedBindingBuilder =
        bind(classOf[InformationProvider]).to(classOf[InformationProviderMockSuccess])
      override def configure(): Unit = super.configure()
    }

    "should provide goClient successfully" in {
      val injector = testInjector(new SuccessUnitTestBinder)
      val infoProvider = injector.get[InformationProvider]
      val statusProvided = pocStatus.copy(goClientProvided = true)
      val r = infoProvider.infoToGoClient(poc, statusAndPW).runSyncUnsafe()
      // assert
      r shouldBe statusAndPW.copy(statusProvided)
    }

    "should not provide goClient if already true" in {
      val injector = testInjector(new SuccessUnitTestBinder)
      val infoProvider = injector.get[InformationProvider]
      val statusAndPWProvided = statusAndPW.copy(pocStatus = pocStatus.copy(goClientProvided = true))
      val r = infoProvider.infoToGoClient(poc, statusAndPWProvided).runSyncUnsafe()
      // assert
      r shouldBe statusAndPWProvided
    }

    "should provide certifyApi successfully" in {
      val injector = testInjector(new SuccessUnitTestBinder)
      val infoProvider = injector.get[InformationProvider]
      val statusProvided = pocStatus.copy(certifyApiProvided = true)
      val r = infoProvider.infoToCertifyAPI(poc, statusAndPW, tenant).runSyncUnsafe()
      // assert
      r.status shouldBe statusProvided
    }

    "should not provide certifyApi if already true" in {
      val injector = testInjector(new SuccessUnitTestBinder)
      val infoProvider = injector.get[InformationProvider]
      val statusProvided = pocStatus.copy(certifyApiProvided = true)
      val r = infoProvider.infoToCertifyAPI(poc, statusAndPW.copy(pocStatus = statusProvided), tenant).runSyncUnsafe()
      // assert
      r.status shouldBe statusProvided
    }

    class WrongURLUnitTestBinder extends DefaultUnitTestBinder {
      override def InformationProvider: ScopedBindingBuilder =
        bind(classOf[InformationProvider]).to(classOf[InformationProviderMockWrongURL])
      override def configure(): Unit = super.configure()
    }

    "should throw exception when providing goClient but url is wrong" in {
      val injector = testInjector(new WrongURLUnitTestBinder)
      val infoProvider = injector.get[InformationProvider]
      val errorState =
        pocStatus.copy(errorMessage = Some("an error occurred when providing info to go client; missing scheme"))

      assertThrows[PocCreationError](infoProvider.infoToGoClient(poc, statusAndPW).runSyncUnsafe())
      //test the same in a different way
      infoProvider
        .infoToGoClient(poc, statusAndPW)
        .onErrorHandle {
          case PocCreationError(state, _) =>
            state.status shouldBe errorState
        }.runSyncUnsafe()
    }

    "should throw exception when providing certifyApi but url is wrong" in {
      val injector = testInjector(new WrongURLUnitTestBinder)
      val infoProvider = injector.get[InformationProvider]
      val errorState =
        pocStatus.copy(errorMessage = Some("an error occurred when providing info to certify api; missing scheme"))

      assertThrows[PocCreationError](infoProvider.infoToCertifyAPI(poc, statusAndPW, tenant).runSyncUnsafe())
      //test the same in a different way
      val r = infoProvider
        .infoToCertifyAPI(poc, statusAndPW, tenant)
        .onErrorHandle {
          case PocCreationError(state, _) =>
            state.status shouldBe errorState
        }.runSyncUnsafe()
    }
  }

}
