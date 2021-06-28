package com.ubirch.test

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.duration._

object TaskSupport {
  implicit class TaskOps[T](t: Task[T]) {
    def unwrap: T = t.runSyncUnsafe(1.minute)

    def catchError: Throwable =
      t.flatMap(v => Task.raiseError(new RuntimeException(s"Task did not failed but returned value: $v")))
        .onErrorHandle(t => t)
        .runSyncUnsafe(1.minute)
  }
}
