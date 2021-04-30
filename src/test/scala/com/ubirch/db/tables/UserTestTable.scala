package com.ubirch.db.tables
import com.ubirch.models.user.{ User, UserId }
import monix.eval.Task
import scala.collection.mutable

class UserTestTable extends UserRepository {
  private val userDatastore = mutable.Map[UserId, User]()

  override def createUser(user: User): Task[Unit] =
    Task {
      userDatastore += ((user.id, user))
      ()
    }
  override def updateUser(user: User): Task[Unit] =
    Task {
      userDatastore.update(user.id, user)
      ()
    }
  override def deleteUser(id: UserId): Task[Unit] =
    Task {
      userDatastore -= id
      ()
    }
  override def getUser(id: UserId): Task[Option[User]] =
    Task(userDatastore.get(id))
}
