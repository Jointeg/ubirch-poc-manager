package com.ubirch.e2e.controllers

import com.ubirch.e2e.E2ETestBase
import com.ubirch.{ FakeToken, FakeTokenCreator, FakeX509Certs, InjectorHelper }
import sttp.model.{ Method, Methods }

import java.nio.charset.StandardCharsets

trait X509CertTests extends E2ETestBase with Methods {
  def x509SuccessWhenNonBlockingIssuesWithCert[T](
    method: Method,
    path: String,
    createToken: FakeTokenCreator => FakeToken,
    payload: T,
    responseAssertion: String => Unit = _ => ())(
    requestBody: T => String,
    assertion: (InjectorHelper, T) => Unit = (_: InjectorHelper, _: T) => ()
  ): Unit = {
    "be able to successfully perform request if x509 certs are missing the configured intermediate cert" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]
        submit(
          method = method.method,
          path = path,
          body = requestBody(payload).getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> createToken(tokenCreator).prepare, FakeX509Certs.x509HeaderUntilIssuer)
        ) {
          status should equal(200)
          responseAssertion(body)
        }
      }
    }

    "be able to successfully perform a request if x509 certs have a wrong order" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]
        submit(
          method = method.method,
          path = path,
          body = requestBody(payload).getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> createToken(tokenCreator).prepare, FakeX509Certs.x509HeaderWithWrongOrder)
        ) {
          status should equal(200)
          responseAssertion(body)
        }
        assertion(injector, payload)
      }
    }
  }

  def x509ForbiddenWhenHeaderIsInvalid(
    method: Method,
    path: String,
    requestBody: => String,
    createToken: FakeTokenCreator => FakeToken): Unit = {
    "respond with 403 if the X509 header is missing" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]

        submit(
          method = method.method,
          path = path,
          body = requestBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> createToken(tokenCreator).prepare)
        ) {
          status should equal(403)
          assert(body.contains("Forbidden"))
        }
      }
    }

    "respond with 403 if x509 is invalid" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]

        submit(
          method = method.method,
          path = path,
          body = requestBody.getBytes(StandardCharsets.UTF_8),
          headers = Map("authorization" -> createToken(tokenCreator).prepare, FakeX509Certs.invalidX509Header)
        ) {
          status should equal(403)
          assert(body.contains("Forbidden"))
        }
      }
    }

    "respond with 403 if x509 is single cert" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]

        submit(
          method = method.method,
          path = path,
          body = requestBody.getBytes(StandardCharsets.UTF_8),
          headers =
            Map("authorization" -> createToken(tokenCreator).prepare, FakeX509Certs.invalidSingleX509Header)
        ) {
          status should equal(403)
          assert(body.contains("Forbidden"))
        }
      }
    }
  }
}
