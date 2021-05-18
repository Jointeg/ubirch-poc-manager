package com.ubirch

class PocConfigTest extends UnitTestBase {
  "dataSchemaGroupMap" should {
    "success to load as Map[String, String]" in {
      withInjector { injector =>
        val pocConfig = injector.get[PocConfig]
        val expected = Map(
          "DATA_SCHEMA_certification-bvdw-certificate" -> "0aa073cb-89de-49c5-867a-01ae22cd6fb9",
          "DATA_SCHEMA_certification-corona-test" -> "99532141-0835-45a1-ba11-81f10d728d62"
        )
        pocConfig.dataSchemaGroupIdMap shouldBe expected
      }
    }
  }
}
