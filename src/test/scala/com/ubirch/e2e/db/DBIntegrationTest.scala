package com.ubirch.e2e.db
import com.ubirch.db.tables.UserRepository
import com.ubirch.e2e.E2ETestBase
import com.ubirch.models.user.{Email, User, UserId, WaitingForRequiredActions}

import java.util.UUID
import scala.concurrent.duration.DurationInt

class DBIntegrationTest extends E2ETestBase {

  "System" should {
    "should be able to store and retrieve data in DB" in {
      withInjector { injector =>
        val repo = injector.get[UserRepository]
        val userId = UserId(UUID.randomUUID())
        val userEmail = Email("testmail@example.com")
        val res = for {
          _ <- repo.createUser(User(userId, userEmail))
          data <- repo.getUser(userId)
        } yield data

        await(res, 5.seconds) shouldBe Some(User(userId, userEmail, WaitingForRequiredActions))
      }
    }
  }
}
