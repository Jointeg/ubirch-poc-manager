package com.ubirch.db.tables
import com.google.inject.Inject
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.models.user.{ User, UserId }
import monix.eval.Task

trait UserRepository {
  def createUser(user: User): Task[Unit]
  def updateUser(user: User): Task[Unit]
  def deleteUser(id: UserId): Task[Unit]
  def getUser(id: UserId): Task[Option[User]]
}

class UserTable @Inject() (quillJdbcContext: QuillJdbcContext) extends UserRepository {
  import quillJdbcContext.ctx._

  private def createUserQuery(user: User) =
    quote {
      querySchema[User]("poc_manager.users").insert(lift(user))
    }

  private def updateUserQuery(user: User) =
    quote {
      querySchema[User]("poc_manager.users").filter(_.id == lift(user.id)).update(lift(user))
    }

  private def removeUserQuery(id: UserId) =
    quote {
      querySchema[User]("poc_manager.users").filter(_.id == lift(id)).delete
    }

  private def getUserQuery(id: UserId) =
    quote {
      querySchema[User]("poc_manager.users").filter(_.id == lift(id))
    }

  override def createUser(user: User): Task[Unit] = Task(run(createUserQuery(user)))
  override def updateUser(user: User): Task[Unit] = Task(run(updateUserQuery(user)))
  override def deleteUser(id: UserId): Task[Unit] = Task(run(removeUserQuery(id)))
  override def getUser(id: UserId): Task[Option[User]] = Task(run(getUserQuery(id))).map(_.headOption)
}
