package com.ubirch.db.tables

import com.ubirch.models.auth.{ Hash, HashedData }
import monix.eval.Task

import scala.collection.mutable

class KeyHashTestTable extends KeyHashRepository {
  private val keyHashDatastore = mutable.Map[Hash, HashedData]()

  override def insertNewKeyHash(hashedData: HashedData): Task[Unit] =
    Task {
      keyHashDatastore += ((hashedData.hash, hashedData))
      ()
    }
  override def getFirst: Task[Option[HashedData]] = Task(keyHashDatastore.values.headOption)
}
