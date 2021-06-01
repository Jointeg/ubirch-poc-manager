package com.ubirch.services.clock

import com.google.inject.Provider

import java.time.Clock
import javax.inject.Singleton

@Singleton
class ClockProvider extends Provider[Clock] {
  override def get(): Clock = Clock.systemDefaultZone()
}
