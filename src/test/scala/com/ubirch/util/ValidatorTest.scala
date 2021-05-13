package com.ubirch.util

import cats.data.NonEmptyList
import cats.data.Validated.{ Invalid, Valid }
import com.ubirch.ModelCreationHelper.createTenant
import com.ubirch.TestBase
import com.ubirch.services.poc.util.CsvConstants._
import com.ubirch.services.poc.util.ValidatorConstants.phoneValidationError
import com.ubirch.services.poc.util.{ CsvConstants, ValidatorConstants }
import com.ubirch.services.util.Validator
import org.scalatest.prop.{ TableDrivenPropertyChecks, TableFor1 }

class ValidatorTest extends TestBase with TableDrivenPropertyChecks {

  "Validator JValue" should {

    "validate JValue valid" in {
      val json = """{"test": "1", "testArray": ["entry1", "entry2"]}"""
      val jvalue = Validator.validateJson(jsonConfig, json)
      assert(jvalue.isValid)
    }

    "validate None if emtpy string" in {
      val json = ""
      val validated = Validator.validateJson(jsonConfig, json)
      assert(validated.isValid)
      validated
        .map(v => assert(v.isEmpty))
    }

    "validate broken json invalid" in {
      val json = """{"test": "1", "testArray: ["entry1", "entry2"]}"""
      val validated = Validator.validateJson(jsonConfig, json)

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
      val validated = Validator.validateEmail(managerEmail, str)
      assert(validated.isValid)
    }

    "validate broken Email invalid" in {
      val str = "test@test@.de"
      val validated = Validator.validateEmail(managerEmail, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.emailError(managerEmail))
        }
    }
  }

  "Validator URL" should {

    "validate URL valid" in {
      val str = "http://www.ubirch.com"
      val validated = Validator.validateURL(logoUrl, str, "true")
      assert(validated.isValid)
    }

    "validate broken URL invalid" in {
      val str = "www.ubirch.comX"
      val validated = Validator.validateURL(logoUrl, str, "true")
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == "column logo_url must contain a proper url")
        }
    }

    "validate URL valid, if certifyApp column contains errors " in {
      val str = "www.ubirch.com"
      val validated = Validator.validateURL(logoUrl, str, "CtrueX")
      assert(validated.isValid)
    }
  }

  "Validator Boolean" should {

    "validate 'TRUE' valid" in {
      val str = "TRUE"
      val validated = Validator.validateBoolean(certifyApp, str)
      assert(validated.isValid)
    }

    "validate 'TRue' valid" in {
      val str = "TRue"
      val validated = Validator.validateBoolean(certifyApp, str)
      assert(validated.isValid)
    }

    "validate 'false' valid" in {
      val str = "false"
      val validated = Validator.validateBoolean(certifyApp, str)
      assert(validated.isValid)
    }

    "validate 'XTrue' invalid" in {
      val str = "XTrue"
      val validated = Validator.validateBoolean(certifyApp, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.booleanError(certifyApp))
        }
    }

    "validate 'false_' invalid" in {
      val str = "false_"
      val validated = Validator.validateBoolean(certifyApp, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.booleanError(certifyApp))
        }
    }
  }

  "Validator Client Cert" should {

    val tenant = createTenant()
    "validate 'TRUE' valid" in {
      val str = "TRUE"
      val validated = Validator.validateClientCert(clientCert, str, tenant)
      assert(validated.isValid)
    }

    "validate 'tryx' valid" in {
      val str = "tryx"
      val validated = Validator.validateClientCert(clientCert, str, tenant)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.booleanError(clientCert))
        }
    }

    "validate 'False' valid" in {
      val str = "False"
      val validated = Validator.validateClientCert(clientCert, str, tenant)
      assert(validated.isValid)
    }

    val tenantWithoutCert = createTenant(clientCert = None)
    "validate 'False' invalid when tenant does not have a client cert" in {
      val str = "False"
      val validated = Validator.validateClientCert(clientCert, str, tenantWithoutCert)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == ValidatorConstants.clientCertError(clientCert))
        }
    }
  }

  "Validator Phone" should {

    "validate phone number example 1 valid" in {
      val str = "+49327387862"
      val validated = Validator.validatePhone(phone, str)
      assert(validated.isValid)
    }

    "validate phone number  example 2 valid" in {
      val str = "+4930-7387862"
      val validated = Validator.validatePhone(phone, str)
      assert(validated.isValid)
    }

    "validate phone number 0187-73878989 valid" in {
      val str = "0187-73878989"
      val validated = Validator.validatePhone(phone, str)
      assert(validated.isValid)
    }

    "validate phone number +49-301267863 valid" in {
      val str = "+49-301267863"
      val validated = Validator.validatePhone(phone, str)
      assert(validated.isValid)
    }

    "validate phone number  example 5 valid" in {
      val str = "030786862834"
      val validated = Validator.validatePhone(phone, str)
      assert(validated.isValid)
    }

    "validate phone number example 1 invalid" in {
      val str = "+4932738x7862"
      val validated = Validator.validatePhone(phone, str)
      assert(validated.isInvalid)
      validated
        .leftMap(_.toList.mkString(comma))
        .leftMap { error =>
          assert(error == phoneValidationError(CsvConstants.phone))
        }
    }
  }

  "Validator String" should {

    "validate String valid if not empty" in {
      val str = "t"
      val validated = Validator.validateString(pocName, str)
      assert(validated.isValid)
    }

    "validate empty string invalid" in {
      val str = ""
      val validated = Validator.validateString(pocName, str)
      assert(validated.isInvalid)
    }
  }

  "Validator StringOption" should {

    "validate String Option valid if not empty" in {
      val str = "t"
      val validated = Validator.validateStringOption(str)
      assert(validated.isValid)
      validated.map(stringOpt => assert(stringOpt.contains(str)))
    }

    "validate String Option valid if empty" in {
      val str = ""
      val validated = Validator.validateStringOption(str)
      assert(validated.isValid)
      validated.map(stringOpt => assert(stringOpt.isEmpty))
    }
  }

  "Validator Zipcode" should {

    "validate zipcode valid if not empty" in {
      val str = "98707"
      val validated = Validator.validateZipCode(zipcode, str)
      assert(validated.isValid)
      validated.map(zipcode => assert(zipcode.isInstanceOf[Int]))
    }

    "validate empty zipcode invalid" in {
      val str = ""

      val validated = Validator.validateZipCode(zipcode, str)
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
    "❤️"
  )

  "Validator PocName" must {
    "not have empty header" in {
      val validated = Validator.validatePocName("poc_name*", " ")
      validated mustBe Invalid(NonEmptyList.fromListUnsafe(List("column poc_name* cannot be empty")))
    }
  }

  forAll(validPocName) { name =>
    s"valid $name" in {
      val validated = Validator.validatePocName("poc_name*", name)
      validated mustBe Valid(name)
    }
  }

  forAll(invalidPocName) { name =>
    s"invalid $name" in {
      val validated = Validator.validatePocName("poc_name*", name)
      validated mustBe Invalid(NonEmptyList.fromListUnsafe(List("column poc_name* must contain a valid poc name")))
    }
  }
}
