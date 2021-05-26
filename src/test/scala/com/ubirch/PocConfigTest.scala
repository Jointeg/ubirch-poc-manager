package com.ubirch

class PocConfigTest extends UnitTestBase {
  "dataSchemaGroupMap" should {
    "success to load as Map[String, String]" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val expected = Map(
          "ub_vac_app" -> "uuid1",
          "ub_vac_api" -> "uuid2",
          "bmg_vac_api" -> "a784b7cf-d798-49c8-adb1-c91a122904f5",
          "data-schema-id" -> "a784b7cf-d798-49c8-adb1-c91a122904f5"
        )
        pocConfig.pocTypeDataSchemaMap shouldBe expected
      }
    }

    "success to load pocTypeEndpointMap config as Map[String, String]" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val expected = Map(
          "ub_vac_app" -> "endpoint1",
          "ub_vac_api" -> "endpoint2",
          "bmg_vac_api" -> "endpoint3")
        pocConfig.pocTypeEndpointMap shouldBe expected
      }
    }
  }
}
