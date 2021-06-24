package com.ubirch

import cats.effect.concurrent.Ref
import com.google.inject.binder.ScopedBindingBuilder
import com.ubirch.db.tables._
import monix.eval.Task
import monix.execution.{ CancelableFuture, Scheduler }
import monix.reactive.Observable
import org.scalatest.{ EitherValues, OptionValues }
import org.scalatra.test.scalatest.ScalatraWordSpec

import java.util.concurrent.Executors
import scala.concurrent.duration.{ Duration, DurationInt, DurationLong }
import scala.concurrent.{ Await, ExecutionContext, ExecutionContextExecutor, Future }

trait ExecutionContextsTests {
  implicit lazy val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))
  implicit lazy val scheduler: Scheduler = monix.execution.Scheduler(ec)
}

trait Awaits {

  def await[T](future: Future[T]): T = await(future, Duration.Inf)

  def await[T](future: Future[T], atMost: Duration): T = Await.result(future, atMost)

  def await[T](observable: Observable[T], atMost: Duration)(implicit scheduler: Scheduler): Seq[T] = {
    val future = observable.foldLeftL(Nil: Seq[T])((a, b) => a ++ Seq(b)).runToFuture
    Await.result(future, atMost)
  }

  def await[T](task: Task[T], atMost: Duration = 5.seconds)(implicit scheduler: Scheduler): T = {
    val future = task.runToFuture
    Await.result(future, atMost)
  }

  private def sleepUntil(condition: Task[Boolean], atMost: Duration)(implicit scheduler: Scheduler): Task[Unit] = {
    for {
      goOn <- condition
      _ <-
        if (goOn) {
          Task.unit
        } else if (!goOn && atMost.toMillis > 0) {
          Task.sleep(100.millis).flatMap(_ => sleepUntil(condition, atMost - 100.millis))
        } else {
          Task.raiseError(new RuntimeException(
            s"Could not complete specified task due to timeout"))
        }
    } yield ()
  }

  def awaitUntil[T](task: Task[T], condition: Task[Boolean], atMost: Duration)(implicit scheduler: Scheduler): T = {
    val futResult = (for {
      result <- task
      _ <- sleepUntil(condition, atMost)
    } yield result).runToFuture
    Await.result(futResult, atMost)
  }

  def awaitUntil[T](observable: Observable[T], condition: Task[Boolean], atMost: Duration)(implicit
  scheduler: Scheduler): Unit = {
    val result = for {
      cancelable <- Task(observable.subscribe())
      _ <- sleepUntil(condition, atMost)
    } yield cancelable.cancel()
    Await.result(result.runToFuture, atMost)
  }

  def awaitForTwoTicks[T](observable: Observable[T], atMost: Duration = 5.seconds)(implicit
  scheduler: Scheduler): CancelableFuture[Unit] = {
    val res = for {
      ref <- Ref.of[Task, Int](0)
      cancelable <-
        Task(observable.doOnNext(_ => {
          ref.update(current => current + 1)
        }).subscribe()).timeout(atMost.toMillis.millis)
      _ <- sleepUntil(ref.get.map(elems => elems >= 2), atMost)
    } yield cancelable.cancel()
    Await.ready(res.runToFuture, atMost)
  }
}

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

class UnitTestInjectorHelper() extends InjectorHelper(List(new DefaultUnitTestBinder))

class DefaultUnitTestBinder extends Binder {

  override def PocRepository: ScopedBindingBuilder =
    bind(classOf[PocRepository]).to(classOf[PocRepositoryMock])

  override def PocLogoRepository: ScopedBindingBuilder =
    bind(classOf[PocLogoRepository]).to(classOf[PocLogoRepositoryMock])

}
