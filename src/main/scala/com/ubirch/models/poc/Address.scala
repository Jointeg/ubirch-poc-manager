package com.ubirch.models.poc

import io.getquill.Embedded

case class Address(
  street: String,
  houseNumber: String,
  additionalAddress: Option[String] = None,
  zipcode: Int,
  city: String,
  county: Option[String] = None,
  federalState: Option[String],
  country: String
) extends Embedded