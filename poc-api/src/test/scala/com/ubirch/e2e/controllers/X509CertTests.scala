package com.ubirch.e2e.controllers

import com.ubirch.e2e.E2ETestBase
import com.ubirch.{ FakeToken, FakeTokenCreator, FakeX509Certs, InjectorHelper }
import org.scalatest.AppendedClues
import sttp.model.{ Method, Methods }

import java.nio.charset.StandardCharsets

trait X509CertTests extends E2ETestBase with Methods with AppendedClues {

  def x509SuccessWhenNonBlockingIssuesWithCert[T](
    method: Method,
    path: String,
    createToken: FakeTokenCreator => FakeToken,
    payload: T,
    responseAssertion: (String, T) => Unit = (_: String, _: T) => (),
    before: (InjectorHelper, T) => Unit = (_: InjectorHelper, _: T) => (),
    requestBody: T => String = (_: T) => "",
    assertion: (InjectorHelper, T) => Unit = (_: InjectorHelper, _: T) => ()
  ): Unit = {
    val bodyToBytes = (p: T) =>
      if (Seq(POST, PUT, PATCH).contains(method)) requestBody(p).getBytes(StandardCharsets.UTF_8)
      else null

    "be able to successfully perform request if x509 certs are missing the configured intermediate cert" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]
        before(injector, payload)

        submit(
          method = method.method,
          path = path,
          body = bodyToBytes(payload),
          headers = Map("authorization" -> createToken(tokenCreator).prepare, FakeX509Certs.x509HeaderUntilIssuer)
        ) {
          status should equal(200) withClue s"Error response: $body"
          responseAssertion(body, payload)
        }
        assertion(injector, payload)
      }
    }

    "be able to successfully perform a request if x509 certs have a wrong order" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]
        before(injector, payload)

        submit(
          method = method.method,
          path = path,
          body = bodyToBytes(payload),
          headers = Map("authorization" -> createToken(tokenCreator).prepare, FakeX509Certs.x509HeaderWithWrongOrder)
        ) {
          status should equal(200) withClue s"Error response: $body"
          responseAssertion(body, payload)
        }
        assertion(injector, payload)
      }
    }
  }

  def x509ForbiddenWhenHeaderIsInvalid(
    method: Method,
    path: String,
    requestBody: => String = "",
    createToken: FakeTokenCreator => FakeToken,
    before: InjectorHelper => Unit = _ => Unit): Unit = {
    val bodyToBytes = (b: String) =>
      if (Seq(POST, PUT, PATCH).contains(method)) b.getBytes(StandardCharsets.UTF_8)
      else null

    "respond with 403 if the X509 header is missing" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]
        before(injector)

        submit(
          method = method.method,
          path = path,
          body = bodyToBytes(requestBody),
          headers = Map("authorization" -> createToken(tokenCreator).prepare)
        ) {
          status should equal(403) withClue s"Error response: $body"
          assert(body.contains("Forbidden"))
        }
      }
    }

    "respond with 403 if x509 is invalid" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]
        before(injector)

        submit(
          method = method.method,
          path = path,
          body = bodyToBytes(requestBody),
          headers = Map("authorization" -> createToken(tokenCreator).prepare, FakeX509Certs.invalidX509Header)
        ) {
          status should equal(403) withClue s"Error response: $body"
          assert(body.contains("Forbidden"))
        }
      }
    }

    "respond with 403 if x509 is single cert" in {
      withInjector { injector =>
        val tokenCreator = injector.get[FakeTokenCreator]
        before(injector)

        submit(
          method = method.method,
          path = path,
          body = bodyToBytes(requestBody),
          headers =
            Map("authorization" -> createToken(tokenCreator).prepare, FakeX509Certs.invalidSingleX509Header)
        ) {
          status should equal(403) withClue s"Error response: $body"
          assert(body.contains("Forbidden"))
        }
      }
    }
  }
}
