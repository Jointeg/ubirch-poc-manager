package com.ubirch

class PocConfigTest extends UnitTestBase {
  "dataSchemaGroupMap" should {
    "success to load as Map[String, String]" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val expected = Map(
          "certification-vaccination" -> "a784b7cf-d798-49c8-adb1-c91a122904f5",
          "data-schema-id" -> "c618b7cf-d798-49c8-adb1-c91a122904f6")
        pocConfig.dataSchemaGroupMap shouldBe expected
      }
    }
  }
}