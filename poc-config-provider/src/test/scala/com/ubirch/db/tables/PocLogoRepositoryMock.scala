package com.ubirch.db.tables

import com.ubirch.models.poc.PocLogo
import monix.eval.Task

import java.util.UUID
import javax.inject.Singleton
import scala.collection.mutable

@Singleton
class PocLogoRepositoryMock extends PocLogoRepository {
  private val pocLogoDatastore = mutable.Map[UUID, PocLogo]()

  override def getPocLogoById(pocId: UUID): Task[Option[PocLogo]] = Task {
    pocLogoDatastore.get(pocId)
  }
}
