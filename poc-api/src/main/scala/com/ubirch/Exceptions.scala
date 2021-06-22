package com.ubirch

import scala.util.control.NoStackTrace

abstract class ServiceException(message: String) extends Exception(message) with NoStackTrace {
  val name: String = this.getClass.getCanonicalName
}

case class InvalidClaimsException(message: String, value: String) extends ServiceException(message)
case class InvalidX509Exception(message: String) extends ServiceException(message)
