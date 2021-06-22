package com.ubirch.services.poc.util

sealed trait CsvException extends Exception
case class HeaderCsvException(errorMsg: String) extends CsvException
case class EmptyCsvException(errorMsg: String) extends CsvException
