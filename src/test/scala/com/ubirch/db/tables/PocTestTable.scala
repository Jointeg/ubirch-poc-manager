package com.ubirch.db.tables
import com.google.inject.Inject
import com.ubirch.models.poc.{Completed, Poc, PocStatus}
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocTestTable @Inject()(pocStatusTable: PocStatusTestTable) extends PocRepository {
  private val pocDatastore = mutable.Map[UUID, Poc]()

  override def createPoc(poc: Poc): Task[UUID] =
    Task {
      pocDatastore += ((poc.id, poc))
      poc.id
    }

  override def updatePoc(poc: Poc): Task[UUID] =
    Task {
      pocDatastore.update(poc.id, poc)
      poc.id
    }

  override def getAllPocsByTenantId(tenantId: UUID): Task[List[Poc]] =
    Task {
      pocDatastore.collect {
        case (_, poc: Poc) if poc.tenantId == tenantId => poc
      }.toList
    }

  override def getAllUncompletedPocs(): Task[List[Poc]] =
    Task {
      pocDatastore.collect {
        case (_, poc: Poc) if poc.status != Completed => poc
      }.toList
    }

  override def getPoc(pocId: UUID): Task[Option[Poc]] = {
    Task(pocDatastore.get(pocId))
  }

  override def deletePoc(id: UUID): Task[Unit] =
    Task(pocDatastore.remove(id))

  override def createPocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Unit] = {
    Task{
      pocDatastore += ((poc.id, poc))
      pocStatusTable.createPocStatus(pocStatus)
    }
  }

  override def updatePocAndStatus(poc: Poc, pocStatus: PocStatus): Task[Unit] = {
    Task{
      pocDatastore.update(poc.id, poc)
      pocStatusTable.updatePocStatus(pocStatus)
    }

  }

}
