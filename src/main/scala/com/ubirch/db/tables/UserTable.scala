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

  private def createExampleDataQuery(exampleData: User) =
    quote {
      querySchema[User]("poc_manager.users").insert(lift(exampleData))
    }

  private def updateExampleDataQuery(exampleData: User) =
    quote {
      querySchema[User]("poc_manager.users").filter(_.id == lift(exampleData.id)).update(lift(exampleData))
    }

  private def removeExampleDataQuery(id: UserId) =
    quote {
      querySchema[User]("poc_manager.users").filter(_.id == lift(id)).delete
    }

  private def getExampleDataQuery(id: UserId) =
    quote {
      querySchema[User]("poc_manager.users").filter(_.id == lift(id))
    }

  override def createUser(user: User): Task[Unit] = Task(run(createExampleDataQuery(user)))
  override def updateUser(user: User): Task[Unit] = Task(run(updateExampleDataQuery(user)))
  override def deleteUser(id: UserId): Task[Unit] = Task(run(removeExampleDataQuery(id)))
  override def getUser(id: UserId): Task[Option[User]] = Task(run(getExampleDataQuery(id))).map(_.headOption)
}
