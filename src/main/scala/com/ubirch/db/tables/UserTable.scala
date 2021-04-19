package com.ubirch.db.tables
import com.google.inject.Inject
import com.ubirch.db.context.QuillJdbcContext
import com.ubirch.db.models.User
import monix.eval.Task

import java.util.UUID

trait UserRepository {
  def createUser(exampleData: User): Task[Unit]
  def updateUser(exampleData: User): Task[Unit]
  def deleteUser(id: UUID): Task[Unit]
  def getUser(id: UUID): Task[User]
}

class UserTable @Inject() (quillJdbcContext: QuillJdbcContext) extends UserRepository {
  import quillJdbcContext.ctx._

  private def createExampleDataQuery(exampleData: User) =
    quote {
      querySchema[User]("users").insert(lift(exampleData))
    }

  private def updateExampleDataQuery(exampleData: User) =
    quote {
      querySchema[User]("users").filter(_.id == lift(exampleData.id)).update(lift(exampleData))
    }

  private def removeExampleDataQuery(id: UUID) =
    quote {
      querySchema[User]("users").filter(_.id == lift(id)).delete
    }

  private def getExampleDataQuery(id: UUID) =
    quote {
      querySchema[User]("users").filter(_.id == lift(id))
    }

  override def createUser(exampleData: User): Task[Unit] = Task(run(createExampleDataQuery(exampleData)))
  override def updateUser(exampleData: User): Task[Unit] = Task(run(updateExampleDataQuery(exampleData)))
  override def deleteUser(id: UUID): Task[Unit] = Task(run(removeExampleDataQuery(id)))
  override def getUser(id: UUID): Task[User] = Task(run(getExampleDataQuery(id))).map(_.head)
}
