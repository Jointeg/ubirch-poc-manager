package com.ubirch

class PocConfigTest extends UnitTestBase {
  "dataSchemaGroupMap" should {
    "success to load as Map[String, String]" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val expected = Map(
          "group-name1" -> "uuid1",
          "group-name2" -> "uuid2",
          "certification-vaccination" -> "a784b7cf-d798-49c8-adb1-c91a122904f5",
          "data-schema-id" -> "a784b7cf-d798-49c8-adb1-c91a122904f5"
        )
        pocConfig.dataSchemaGroupMap shouldBe expected
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
