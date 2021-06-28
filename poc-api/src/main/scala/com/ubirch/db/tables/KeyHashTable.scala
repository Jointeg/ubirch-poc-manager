package com.ubirch.db.tables

import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.auth.HashedData
import monix.eval.Task

import javax.inject.Inject

trait KeyHashRepository {
  def insertNewKeyHash(hashedData: HashedData): Task[Unit]
  def getFirst: Task[Option[HashedData]]
}

class KeyHashTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends KeyHashRepository {

  import QuillMonixJdbcContext.ctx._

  private def insertNewKeyHashQuery(hashedData: HashedData) =
    quote {
      querySchema[HashedData]("poc_manager.key_hash_table").insert(lift(hashedData))
    }

  private def getFirstQuery =
    quote {
      querySchema[HashedData]("poc_manager.key_hash_table").take(1)
    }

  override def insertNewKeyHash(hashedData: HashedData): Task[Unit] =
    run(insertNewKeyHashQuery(hashedData)).void

  override def getFirst: Task[Option[HashedData]] =
    run(getFirstQuery).map(_.headOption)
}
