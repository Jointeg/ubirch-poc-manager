package com.ubirch.db.tables

import com.google.inject.Inject
import com.ubirch.models.poc.Poc
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocRepositoryMock @Inject() () extends PocRepository {
  private val pocDatastore = mutable.Map[UUID, Poc]()

  override def getPoc(pocId: UUID): Task[Option[Poc]] = {
    Task(pocDatastore.get(pocId))
  }

  def createPoc(poc: Poc): Task[Unit] = {
    Task(pocDatastore.update(poc.id, poc))
  }
}
