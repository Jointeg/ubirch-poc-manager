package com.ubirch.models.poc

sealed trait CustomDataSchemaSetting

object CustomDataSchemaSetting {
  val schemaMap: Map[String, CustomDataSchemaSetting] = Map(
    VaccinationV3.SchemaId -> VaccinationV3.Default,
    CoronaTest.SchemaId -> CoronaTest.Default,
    VaccinationBmgV2.SchemaId -> VaccinationBmgV2.Default,
    BvdwCertificate.SchemaId -> BvdwCertificate.default
  )
}

case class VaccinationV3(vaccines: Seq[String], verificationRoute: String, verificationServerUrl: String)
  extends CustomDataSchemaSetting
object VaccinationV3 {
  val SchemaId = "vaccination-v3"
  val Default = VaccinationV3(Seq.empty[String], "vp-3", "https://verify.govdigital.de/")
}

case class CoronaTest(testTypes: Seq[String], verificationRoute: String, verificationServerUrl: String)
  extends CustomDataSchemaSetting
object CoronaTest {
  val SchemaId = "corona-test"
  val Default = CoronaTest(Seq("PCR", "Antigen"), "gd", "https://verify.govdigital.de/")
}

case class VaccinationBmgV2(
  showBMGImprint: Boolean,
  hideUbirchSeal: Boolean,
  hideVerificationUrl: Boolean,
  hideLogoOnForm: Boolean,
  infoUrl: String,
  expirationOffset: Int,
  vaccines: Seq[String])
  extends CustomDataSchemaSetting
object VaccinationBmgV2 {
  val SchemaId = "vaccination-bmg-v2"
  val Default = VaccinationBmgV2(
    true,
    true,
    true,
    true,
    "https://www.digitaler-impfnachweis-app.de/impfzertifikat-ausstellen/",
    365,
    Seq.empty[String])
}

case class BvdwCertificate() extends CustomDataSchemaSetting

object BvdwCertificate {
  val SchemaId = "bvdw-certificate"
  val default = BvdwCertificate()
}
