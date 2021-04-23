package com.ubirch.models.poc

import io.getquill.Embedded

case class PocManager(
                       managerSurname: String,
                       managerName: String,
                       managerEmail: String,
                       managerMobilePhone: String
                     ) extends Embedded
