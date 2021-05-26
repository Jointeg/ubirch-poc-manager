package com.ubirch.services.poc

import com.google.inject.AbstractModule
import com.google.inject.binder.ScopedBindingBuilder
import com.ubirch.ModelCreationHelper.{ createPoc, createPocStatus, createTenant }
import com.ubirch.models.NamespacedUUID
import com.ubirch.models.poc.{ DeviceId, Poc, PocStatus }
import com.ubirch.services.poc.PocTestHelper.createPocTriple
import com.ubirch.{ Awaits, Binder, DefaultUnitTestBinder, InjectorHelper }
import monix.execution.Scheduler
import org.json4s.Formats
import org.json4s.native.Serialization.read
import org.scalatest.TryValues
import org.scalatra.test.scalatest.ScalatraWordSpec

import java.util.UUID
import scala.util.Try

class InformationProviderSpec extends ScalatraWordSpec with Awaits with TryValues {

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

    class CertHandlerReturnErrorBinder extends DefaultUnitTestBinder {
      override def CertHandler: ScopedBindingBuilder =
        bind(classOf[CertHandler]).to(classOf[ErrorReturningCertHandler])
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
      val statusAndPWProvided = statusAndPW.copy(status = pocStatus.copy(goClientProvided = true))
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
      val r = infoProvider.infoToCertifyAPI(poc, statusAndPW.copy(status = statusProvided), tenant).runSyncUnsafe()
      // assert
      r.status shouldBe statusProvided
    }

    "should not provide certifyApi if clientCertRequired == true but sharedAuthCertId is not set up on PoC level" in {
      val injector = testInjector(new SuccessUnitTestBinder)
      val infoProvider = injector.get[InformationProvider]
      val pocWithoutSharedAuthCertId = poc.copy(sharedAuthCertId = None, clientCertRequired = true)
      val r = Try(infoProvider.infoToCertifyAPI(pocWithoutSharedAuthCertId, statusAndPW, tenant).runSyncUnsafe())
      r.failure.exception.asInstanceOf[PocCreationError].pocAndStatus.status.certifyApiProvided shouldBe false
      r.failure.exception.asInstanceOf[PocCreationError].pocAndStatus.status shouldBe pocStatus.copy(errorMessage =
        Some("Tried to obtain shared auth cert ID from PoC but it was not defined"))
    }

    "should not provide certifyApi if clientCertRequired == true but CertManager responds with error" in {
      val injector = testInjector(new CertHandlerReturnErrorBinder)
      val infoProvider = injector.get[InformationProvider]
      val pocWithoutSharedAuthCertId = poc.copy(sharedAuthCertId = Some(UUID.randomUUID()), clientCertRequired = true)
      val r = Try(infoProvider.infoToCertifyAPI(pocWithoutSharedAuthCertId, statusAndPW, tenant).runSyncUnsafe())
      r.failure.exception.asInstanceOf[PocCreationError].pocAndStatus.status.certifyApiProvided shouldBe false
      r.failure.exception.asInstanceOf[PocCreationError].pocAndStatus.status shouldBe pocStatus.copy(errorMessage =
        Some("Requested CertManager for shared auth cert but it responded with error get certificate error"))
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
      infoProvider
        .infoToCertifyAPI(poc, statusAndPW, tenant)
        .onErrorHandle {
          case PocCreationError(state, _) =>
            state.status shouldBe errorState
        }.runSyncUnsafe()
    }
  }

  "getCertifyApiBody" should {
    "throw exception if pocType is unknown " in {
      val injector = testInjector(new Binder())
      val infoProvider = injector.get[InformationProviderImpl]
      val (poc, status, tenant) = createPocTriple()
      val badPocType = poc.copy(pocType = "xxx")
      assertThrows[PocCreationError](infoProvider.getCertifyApiBody(
        badPocType,
        StatusAndPW(status, "devicePassword"),
        tenant).runSyncUnsafe())
    }

    "throw exception if clientCert is not required, but tenant doesn't have a cert either " in {
      val injector = testInjector(new Binder())
      val infoProvider = injector.get[InformationProviderImpl]
      val (poc, status, tenant) = createPocTriple()
      val badPocType = poc.copy(clientCertRequired = false)
      val badTenant = tenant.copy(sharedAuthCert = None)

      val body = infoProvider.getCertifyApiBody(badPocType, StatusAndPW(status, "devicePassword"), badTenant)
      assertThrows[PocCreationError](body.runSyncUnsafe())
    }

    "succeed to create body with tenant's shared auth cert " in {
      val injector = testInjector(new Binder())
      val infoProvider = injector.get[InformationProviderImpl]
      implicit val formats: Formats = injector.get[Formats]

      val (poc, status, tenant) = createPocTriple()
      val goodPoc = poc.copy(clientCertRequired = false)
      val pw = "devicePassword"

      val body = infoProvider.getCertifyApiBody(goodPoc, StatusAndPW(status, pw), tenant)
      val registerDevice = body.runSyncUnsafe()
      val parsedObject = read[RegisterDeviceCertifyAPI](registerDevice)

      parsedObject.role shouldBe Some(poc.roleName)
      parsedObject.uuid shouldBe poc.getDeviceId
      parsedObject.password shouldBe pw
      parsedObject.cert.isDefined shouldBe true
      parsedObject.name shouldBe poc.pocName
      parsedObject.location shouldBe None
    }

    "succeed to create body without role but with location" in {
      val injector = testInjector(new Binder())
      val infoProvider = injector.get[InformationProviderImpl]
      implicit val formats: Formats = injector.get[Formats]

      val (poc, status, tenant) = createPocTriple()
      val goodPoc = poc.copy(clientCertRequired = false, pocType = "bmg_vac_api")
      val pw = "devicePassword"

      val body = infoProvider.getCertifyApiBody(goodPoc, StatusAndPW(status, pw), tenant)
      val registerDevice = body.runSyncUnsafe()
      val parsedObject = read[RegisterDeviceCertifyAPI](registerDevice)

      parsedObject.role shouldBe None
      parsedObject.location shouldBe Some(poc.externalId)
    }
  }

}
