package com.ubirch

import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.{EitherValues, OptionValues}
import org.scalatra.test.scalatest.ScalatraWordSpec

trait KeycloakBasedTest
  extends ScalatraWordSpec
  with TestContainerForAll
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues {

  override val containerDef: KeycloakContainer.Def = KeycloakContainer.Def()

  def withInjector[A](testCode: TestKeycloakInjectorHelperImpl => A): A = {
    withContainers { keycloakContainer =>
      testCode(new TestKeycloakInjectorHelperImpl(keycloakContainer))
    }
  }
}
