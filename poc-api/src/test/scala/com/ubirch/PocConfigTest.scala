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

    "success to load pocTypeDataSchemaMap config as Map[String, Seq[String]]" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val expected = Map(
          "ub_cust_app" -> Seq("bvdw-certificate"),
          "ub_vac_app" -> Seq("vaccination-v3"),
          "ub_test_app" -> Seq("corona-test"),
          "ub_test_api" -> Seq("corona-test"),
          "bmg_vac_app" -> Seq("vaccination-bmg-v2", "recovery-bmg"),
          "bmg_vac_api" -> Seq("vaccination-bmg-v2")
        )
        pocConfig.pocTypeDataSchemaMap shouldBe expected
      }
    }
  }
}
