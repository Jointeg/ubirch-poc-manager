package com.ubirch.db.tables
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.models.auth.HashedData
import monix.eval.Task

import javax.inject.Inject

trait KeyHashRepository {
  def insertNewKeyHash(hashedData: HashedData): Task[Unit]
  def getFirst: Task[Option[HashedData]]
}

class KeyHashTable @Inject() (quillJdbcContext: QuillJdbcContext) extends KeyHashRepository {

  import quillJdbcContext.ctx._

  private def insertNewKeyHashQuery(hashedData: HashedData) =
    quote {
      querySchema[HashedData]("poc_manager.key_hash").insert(lift(hashedData))
    }

  private def getFirstQuery =
    quote {
      querySchema[HashedData]("poc_manager.key_hash").take(1)
    }

  override def insertNewKeyHash(hashedData: HashedData): Task[Unit] =
    Task(run(insertNewKeyHashQuery(hashedData)))

  override def getFirst: Task[Option[HashedData]] =
    Task(run(getFirstQuery).headOption)
}
