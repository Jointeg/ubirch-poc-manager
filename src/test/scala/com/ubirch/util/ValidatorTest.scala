package com.ubirch.util

import cats.data.NonEmptyList
import cats.data.Validated.{ Invalid, Valid }
import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.TestBase
import com.ubirch.models.tenant.{ API, APP, BMG, Both }
import com.ubirch.services.poc.util.CsvConstants.{ externalId, _ }
import com.ubirch.services.poc.util.ValidatorConstants
import com.ubirch.services.poc.util.ValidatorConstants._
import com.ubirch.services.util.Validator._
import org.scalatest.prop.{ TableDrivenPropertyChecks, TableFor1 }

class ValidatorTest extends TestBase with TableDrivenPropertyChecks {

  "Validator JValue" should {

    val ubirchTenant = createTenant()
    val bmgTenant = createTenant().copy(tenantType = BMG)

    "validate any JValue valid if tenantType Ubirch" in {
      val json = """{"test": "1", "testArray": ["entry1", "entry2"]}"""
      val jvalue = validateJson(jsonConfig, json, ubirchTenant)
      assert(jvalue.isValid)
    }

    "validate empty String valid if tenantType Ubirch" in {
      val json = ""
      val validated = validateJson(jsonConfig, json, ubirchTenant)
      assert(validated.isValid)
      validated
        .map(v => assert(v.isEmpty))
    }

    "validate SealId JValue valid if tenantType BMG" in {

      val json = """{"sealId": "59f25a72-6820-4abe-9e95-5d5c9f1b60a7"}"""
      val jvalue = validateJson(jsonConfig, json, bmgTenant)
      assert(jvalue.isValid)
    }

    "validate Not-SealId JValue invalid if tenantType BMG" in {

      val json = """{"test": "1", "testArray": ["entry1", "entry2"]}"""
      val validated = validateJson(jsonConfig, json, bmgTenant)
      assert(validated.isInvalid)
      validated.leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.jsonErrorWhenTenantTypeBMG(jsonConfig))
        }
    }

    "validate empty String invalid if tenantType BMG" in {
      val json = ""
      val validated = validateJson(jsonConfig, json, bmgTenant)
      assert(validated.isInvalid)
      validated.leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.jsonErrorWhenTenantTypeBMG(jsonConfig))
        }
    }

    "validate broken json invalid" in {
      val json = """{"test": "1", "testArray: ["entry1", "entry2"]}"""
      val validated = validateJson(jsonConfig, json, ubirchTenant)

      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.jsonError(jsonConfig))
        }
    }
  }

  "Validator Email" should {

    "validate Email valid" in {
      val str = "test@test.de"
      val validated = validateEmail(managerEmail, str)
      assert(validated.isValid)
    }

    "validate broken Email invalid" in {
      val str = "test@test@.de"
      val validated = validateEmail(managerEmail, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.emailError(managerEmail))
        }
    }
  }

  "Validator URL" should {

    "validate URL invalid" in {
      val str = "anyurl"
      val validated = validateLogoURL(logoUrl, str, "false")
      assert(validated.isValid)
    }

    "validate URL valid" in {
      val str = "http://www.ubirch.com/logo.png"
      val validated = validateLogoURL(logoUrl, str, "true")
      assert(validated.isValid)
    }

    "validate URL invalid, if schema is missing " in {
      val str = "www.ubirch.com"
      val validated = validateLogoURL(logoUrl, str, "true")
      assert(validated.isInvalid)
    }

    "validate URL invalid if wrong file extension" in {
      val str = "http://www.ubirch.com/"
      val validated = validateLogoURL(logoUrl, str, "true")
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == logoUrlNoValidFileFormatError(logoUrl))
        }
    }

    "validate broken URL invalid" in {
      val str = "www.ubirch.comX"
      val validated = validateLogoURL(logoUrl, str, "true")
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == logoUrlNoValidUrlError(logoUrl))
        }
    }

    "validate URL valid, if certifyApp column contains errors " in {
      val str = "www.ubirch.com"
      val validated = validateLogoURL(logoUrl, str, "CtrueX")
      assert(validated.isValid)
    }
  }

  "Validator Boolean" should {

    "validate 'TRUE' valid" in {
      val str = "TRUE"
      val validated = validateBoolean(certifyApp, str)
      assert(validated.isValid)
    }

    "validate 'TRue' valid" in {
      val str = "TRue"
      val validated = validateBoolean(certifyApp, str)
      assert(validated.isValid)
    }

    "validate 'false' valid" in {
      val str = "false"
      val validated = validateBoolean(certifyApp, str)
      assert(validated.isValid)
    }

    "validate 'XTrue' invalid" in {
      val str = "XTrue"
      val validated = validateBoolean(certifyApp, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.booleanError(certifyApp))
        }
    }

    "validate 'false_' invalid" in {
      val str = "false_"
      val validated = validateBoolean(certifyApp, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.booleanError(certifyApp))
        }
    }
  }

  "Validator Client Cert Admin" should {

    "validate 'TRUE' valid if " in {
      val str = "TRUE"
      val validated = validateClientCertAdmin(clientCert, str)
      assert(validated.isValid)
    }

    "validate 'TRUE' valid if tenant usageType == Both" in {
      val str = "false"
      val validated = validateClientCertAdmin(clientCert, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.clientCertAdminError(clientCert))
        }
    }

    "validate 'tryx' invalid" in {
      val str = "tryx"
      val validated = validateClientCertAdmin(clientCert, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.booleanError(clientCert))
        }
    }
  }

  "Validator Admin Certify App " should {

    "validate 'TRUE' valid" in {
      val str = "TRUE"
      val validated = validateAdminCertifyApp(clientCert, str)
      assert(validated.isValid)
    }

    "validate 'False' invalid " in {
      val str = "False"
      val validated = validateClientCertAdmin(clientCert, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == clientCertAdminError(clientCert))
        }
    }

    "validate 'tryx' invalid" in {
      val str = "tryx"
      val validated = validateClientCertAdmin(clientCert, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == booleanError(clientCert))
        }
    }
  }

  "Validator Client Cert" should {

    val tenant = createTenant()
    "validate 'TRUE' valid if tenant usageType == APP" in {
      val str = "TRUE"
      val validated = validateClientCert(clientCert, str, tenant.copy(usageType = APP))
      assert(validated.isValid)
    }

    "validate 'TRUE' valid if tenant usageType == Both" in {
      val str = "TRUE"
      val validated = validateClientCert(clientCert, str, tenant.copy(usageType = Both))
      assert(validated.isValid)
    }

    "validate 'tryx' invalid" in {
      val str = "tryx"
      val validated = validateClientCert(clientCert, str, tenant)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.booleanError(clientCert))
        }
    }

    "validate 'TRUE' invalid when tenant usageType == API" in {
      val str = "TRUE"
      val validated = validateClientCert(clientCert, str, tenant.copy(usageType = API))
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.organisationalUnitCertError(API, clientCertRequired = true))
        }
    }

    "validate 'False' valid" in {
      val str = "False"
      val validated = validateClientCert(clientCert, str, tenant)
      assert(validated.isValid)
    }

    val tenantWithoutCert = createTenant(sharedAuthCert = None)
    "validate 'False' invalid when tenant does not have a client cert" in {
      val str = "False"
      val validated = validateClientCert(clientCert, str, tenantWithoutCert)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.clientCertError(clientCert))
        }
    }

    "validate 'False' invalid when tenant usageType == APP" in {
      val str = "False"
      val validated = validateClientCert(clientCert, str, tenant.copy(usageType = APP))
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.organisationalUnitCertError(APP, clientCertRequired = false))
        }
    }
  }

  val validPhoneNumbers: TableFor1[String] = Table(
    "1555555555",
    "+4974339296",
    "+46-498-313789",
    "+591 74339296",
    "+1 555 555 5554",
    "0001 5555555555",
    "+4930-7387862"
  )

  val invalidPhoneNumbers: TableFor1[String] = Table(
    "+(591) 7433433",
    "+(591) (4) 6434850",
    "0591 74339296",
    "(0001) 5555555",
    "59145678464",
    "030786862834"
  )

  forAll(validPhoneNumbers) { phoneNumber =>
    s"Validator Phone $phoneNumber" in {
      val validated = validatePhone(phone, phoneNumber)
      assert(validated.isValid)
    }
  }

  forAll(invalidPhoneNumbers) { phoneNumber =>
    s"Validator Phone $phoneNumber" in {
      val validated = validatePhone(phone, phoneNumber)
      assert(validated.isInvalid)
    }
  }

  "Validator String" should {

    "validate String valid if not empty" in {
      val str = "t"
      val validated = validateString(pocName, str)
      assert(validated.isValid)
    }

    "validate empty string invalid" in {
      val str = ""
      val validated = validateString(pocName, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == emptyStringError(pocName))
        }
    }
  }

  "Validator map contains key string" should {

    val ubirchTenant = createTenant()
    val bmgTenant = ubirchTenant.copy(tenantType = BMG)
    val map = Map("ub_vac_app" -> "endpoint1", "ub_vac_api" -> "endpoint2", "bmg_vac_app" -> "endpoint3")

    "validate valid when ubirch pocType and tenantType" in {
      "ub_vac_app".split("_").foreach(println(_))
      val validated = validatePocType(pocType, "ub_vac_app", map, ubirchTenant)
      assert(validated.isValid)
    }

    "validate valid when bmg pocType and tenantType" in {
      val validated = validatePocType(pocType, "bmg_vac_app", map, bmgTenant)
      assert(validated.isValid)
    }

    "validate error if UBIRCH pocType but BMG tenantType" in {
      val validated = validatePocType(pocType, "ub_vac_app", map, bmgTenant)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == pocTypeMustCorrelateWithTenantType(pocType, bmgTenant.tenantType))
        }
    }

    "validate error if BMG pocType but UBIRCH tenantType" in {
      val validated = validatePocType(pocType, "bmg_vac_app", map, ubirchTenant)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == pocTypeMustCorrelateWithTenantType(pocType, ubirchTenant.tenantType))
        }
    }

    "validate error if map doesn't contain string" in {
      val validated = validatePocType(pocType, "set1", map, ubirchTenant)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == mapDoesntContainStringKeyError(pocType, map))
        }
    }

    "validate error if string empty" in {
      val validated = validatePocType(pocType, "", map, ubirchTenant)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == mapDoesntContainStringKeyError(pocType, map))
        }
    }

  }

  private val validBmgExternalId: TableFor1[String] = Table(
    "1" * 9,
    "A" * 9,
    "12AB43",
    "895421ARF",
    "893343112",
    "8",
    "C",
    "DEG123",
    "MB4L532"
  )

  private val inValidBmgExternalId: TableFor1[String] = Table(
    "1" * 10,
    "A" * 10,
    "",
    "123a",
    "1234+BGRD",
    "abc32435",
    "1123 456",
    "BGR RED",
    "AAA@223",
    "❤️❤️❤️❤️",
    "<script>"
  )

  "Validator validationExternalId" should {
    val ubirchTenant = createTenant()
    val bmgTenant = ubirchTenant.copy(tenantType = BMG)

    forAll(validBmgExternalId) { id =>
      s"valid bmg externalId: $id" in {
        val validated = validateExternalId(externalId, id, bmgTenant)
        assert(validated.isValid)
      }
    }

    forAll(inValidBmgExternalId) { id =>
      s"invalid bmg externalId: $id" in {
        val validated = validateExternalId(externalId, id, bmgTenant)
        validated mustBe Invalid(NonEmptyList.fromListUnsafe(
          List("column external_id* must include only digits and capital alphabets and have less than 10 length")))
      }
    }

    forAll(validBmgExternalId) { id =>
      s"valid externalId: $id" in {
        val validated = validateExternalId(externalId, id, ubirchTenant)
        assert(validated.isValid)
      }
    }

    forAll(inValidBmgExternalId) { id =>
      s"valid externalId: $id" in {
        val validated = validateExternalId(externalId, id, ubirchTenant)
        if (id.isEmpty) {
          validated mustBe Invalid(NonEmptyList.fromListUnsafe(List("column external_id* cannot be empty")))
        } else {
          assert(validated.isValid)
        }
      }
    }
  }

  "Validator StringOption" should {

    "validate String Option valid if not empty" in {
      val str = "t"
      val validated = validateStringOption(str)
      assert(validated.isValid)
      validated.map(stringOpt => assert(stringOpt.contains(str)))
    }

    "validate String Option valid if empty" in {
      val str = ""
      val validated = validateStringOption(str)
      assert(validated.isValid)
      validated.map(stringOpt => assert(stringOpt.isEmpty))
    }
  }

  "Validator Zipcode" should {

    "validate zipcode valid if not empty" in {
      val str = "98707"
      val validated = validateZipCode(zipcode, str)
      assert(validated.isValid)
      validated.map(zipcode => assert(zipcode.isInstanceOf[Int]))
    }

    "validate empty zipcode invalid" in {
      val str = ""

      val validated = validateZipCode(zipcode, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.zipCodeLengthError(zipcode))
        }
    }
  }

  private val validPocName: TableFor1[String] = Table(
    "name",
    "Tenant 23",
    "ubirch GmbH",
    "übírçh GmbH",
    "ñÿäñtéç GmbH",
    "Büñðèşgēsünđhẽïtsmnısteríüm",
    "Impfzentrum Hintertupfingen-Süd",
    "prod.ubirch.com",
    "dev@nyantec.com",
    "Ministry of Silly Walks",
    "Maybe-Q",
    "予防接種センター",
    "مركز التطعيم",
    "Κέντρο εμβολιασμού",
    "Центр вакцинации"
  )

  private val invalidPocName: TableFor1[String] = Table(
    "name",
    "ubirch\tGmbH",
    raw"ubirch\u00A0GmbH", // non breaking space
    "ubirch  GmbH",
    " ubirch GmbH",
    "ubirch GmbH ",
    "CN=foo",
    "foo/bar",
    "http://",
    "Warum Thunfische das?",
    "Nein, danke",
    "<script>",
    "\\write18",
    "ub\0irch GmbH",
    "u\\x08ubirch GmbH",
    "❤️❤️❤️❤️"
  )

  "Validator PocName" must {
    "not have empty header" in {
      val validated = validatePocName("poc_name*", " ")
      validated mustBe Invalid(NonEmptyList.fromListUnsafe(List("column poc_name* cannot be shorter than 4")))
    }

    "not have short header less than 4 characters" in {
      val validated = validatePocName("poc_name*", "poc")
      validated mustBe Invalid(NonEmptyList.fromListUnsafe(List("column poc_name* cannot be shorter than 4")))
    }
  }

  forAll(validPocName) { name =>
    s"valid $name" in {
      val validated = validatePocName("poc_name*", name)
      validated mustBe Valid(name)
    }
  }

  forAll(invalidPocName) { name =>
    s"invalid $name" in {
      val validated = validatePocName("poc_name*", name)
      validated mustBe Invalid(NonEmptyList.fromListUnsafe(List("column poc_name* must contain a valid poc name")))
    }
  }
}
