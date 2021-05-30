package com.ubirch

class PocConfigTest extends UnitTestBase {
  "dataSchemaGroupMap" should {
    "success to load as Map[String, String]" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val expected = Map(
          "bvdw-certificate" -> "uuid0",
          "vaccination-v3" -> "uuid1",
          "corona-test" -> "uuid2",
          "vaccination-bmg-v2" -> "uuid3")
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
