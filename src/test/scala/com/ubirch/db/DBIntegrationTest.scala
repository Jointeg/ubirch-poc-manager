package com.ubirch.db

import com.ubirch.E2ETestBase
import com.ubirch.db.models.User
import com.ubirch.db.tables.UserRepository
import com.ubirch.models.user.Email

import java.util.UUID
import scala.concurrent.duration.DurationInt

class DBIntegrationTest extends E2ETestBase {

  "System" should {
    "should be able to store and retrieve data in DB" in {
      withInjector { injector =>
        println(injector.postgreContainer.logs)
        val repo = injector.get[UserRepository]
        val userId = UUID.randomUUID()
        val userEmail = Email("testmail@example.com")
        val res = for {
          _ <- repo.createUser(User(userId, userEmail))
          data <- repo.getUser(userId)
        } yield data

        await(res, 5.seconds) shouldBe User(userId, userEmail)
      }
    }
  }
}
