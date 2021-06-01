package com.ubirch.test

import com.google.inject.Provider

import java.time.{ Clock, Instant, ZoneId }
import javax.inject.Singleton
import scala.concurrent.duration.Duration

@Singleton
class FixedClockProvider extends Provider[Clock] {
  private var clock: Clock = Clock.fixed(Instant.parse("2000-01-01T05:10:15Z"), ZoneId.systemDefault())
  override def get(): Clock = clock

  def advance(duration: Duration): Unit = {
    val tmpClock = clock
    clock = Clock.fixed(tmpClock.instant().minusMillis(duration.toMillis), tmpClock.getZone)
  }
}
