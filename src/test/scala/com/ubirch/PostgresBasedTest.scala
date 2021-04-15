package com.ubirch
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.{EitherValues, OptionValues}
import org.scalatra.test.scalatest.ScalatraWordSpec

trait PostgresBasedTest
  extends ScalatraWordSpec
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues
  with TestContainerForAll {

  override val containerDef: PostgresContainer.Def = PostgresContainer.Def()

  def withInjector[A](testCode: PostgresInjectorHelperImpl => A): A = {
    withContainers { postgresContainer =>
      testCode(new PostgresInjectorHelperImpl(postgresContainer))
    }
  }

}
