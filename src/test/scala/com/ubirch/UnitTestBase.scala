package com.ubirch
import org.scalatest.{ EitherValues, OptionValues }
import org.scalatra.test.scalatest.ScalatraWordSpec

trait UnitTestBase
  extends ScalatraWordSpec
  with ExecutionContextsTests
  with Awaits
  with OptionValues
  with EitherValues {

  def withInjector[A](testCode: UnitTestInjectorHelper => A): A = {
    testCode(new UnitTestInjectorHelper())
  }

}
