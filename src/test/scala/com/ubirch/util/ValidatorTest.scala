package com.ubirch.util

import com.ubirch.TestBase
import com.ubirch.services.poc.util.CsvConstants._
import com.ubirch.services.poc.util.ValidatorConstants.phoneValidationError
import com.ubirch.services.poc.util.{ CsvConstants, ValidatorConstants }
import com.ubirch.services.util.Validator

class ValidatorTest extends TestBase {

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

}
