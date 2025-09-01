package com.voiceexpense.testutil

import java.time.Duration
import java.time.Instant

class FakeClock(start: Instant = Instant.EPOCH) {
    private var current: Instant = start

    fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current = current.plus(duration)
    }

    fun set(instant: Instant) {
        current = instant
    }
}

