package com.ubirch.db.tables

import com.google.inject.Inject
import com.ubirch.db.context.QuillMonixJdbcContext
import com.ubirch.models.user.{ User, UserId }
import monix.eval.Task

trait UserRepository {
  def createUser(user: User): Task[Unit]
  def updateUser(user: User): Task[Unit]
  def deleteUser(id: UserId): Task[Unit]
  def getUser(id: UserId): Task[Option[User]]
}

class UserTable @Inject() (QuillMonixJdbcContext: QuillMonixJdbcContext) extends UserRepository {
  import QuillMonixJdbcContext.ctx._

  private def createUserQuery(user: User) =
    quote {
      querySchema[User]("poc_manager.user_table").insert(lift(user))
    }

  private def updateUserQuery(user: User) =
    quote {
      querySchema[User]("poc_manager.user_table").filter(_.id == lift(user.id)).update(lift(user))
    }

  private def removeUserQuery(id: UserId) =
    quote {
      querySchema[User]("poc_manager.user_table").filter(_.id == lift(id)).delete
    }

  private def getUserQuery(id: UserId) =
    quote {
      querySchema[User]("poc_manager.user_table").filter(_.id == lift(id))
    }

  override def createUser(user: User): Task[Unit] =
    run(createUserQuery(user)).void

  override def updateUser(user: User): Task[Unit] = run(updateUserQuery(user)).void
  override def deleteUser(id: UserId): Task[Unit] = run(removeUserQuery(id)).void
  override def getUser(id: UserId): Task[Option[User]] = run(getUserQuery(id)).map(_.headOption)
}
