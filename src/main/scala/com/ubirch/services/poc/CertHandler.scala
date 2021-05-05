package com.ubirch.services.poc

import com.ubirch.models.poc.{ Poc, PocStatus }
import monix.eval.Task

trait CertHandler {

  def createCert(poc: Poc, status: PocStatus): Task[PocStatus]

  def provideCert(poc: Poc, status: PocStatus): Task[PocStatus]
}

class CertCreatorImpl extends CertHandler {

  override def createCert(poc: Poc, status: PocStatus): Task[PocStatus] = {
    status.clientCertCreated match {
      case Some(false) =>
        Task(status)
      // create client cert, update state and return
      case _ => Task(status)
    }
  }

  override def provideCert(poc: Poc, status: PocStatus): Task[PocStatus] = {
    status.clientCertProvided match {
      case Some(false) =>
        Task(status)
      // provide client cert, update state and return
      case _ => Task(status)
    }
  }
}
