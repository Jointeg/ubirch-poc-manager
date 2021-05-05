package com.ubirch.db.tables
import com.ubirch.models.poc.PocStatus
import monix.eval.Task

import java.util.UUID
import scala.collection.mutable

class PocStatusTestTable extends PocStatusRepository {

  private val pocStatusDatastore = mutable.Map[UUID, PocStatus]()

  override def createPocStatus(pocStatus: PocStatus): Task[Unit] =
    Task {
      pocStatusDatastore += ((pocStatus.pocId, pocStatus))
    }

  override def updatePocStatus(pocStatus: PocStatus): Task[Unit] =
    Task {
      pocStatusDatastore.update(pocStatus.pocId, pocStatus)
    }


  override def getPocStatus(pocStatusId: UUID): Task[Option[PocStatus]] = {
    Task(pocStatusDatastore.get(pocStatusId))
  }

  override def deletePocStatus(pocId: UUID): Task[Unit] =
    Task(pocStatusDatastore.remove(pocId))
}
