package com.schwanitz.data.rateLimit

import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class RateLimiter(private val maxPerMinute: Int) {
    private val slots = mutableListOf<Long>()
    private val lock = Any()

    suspend fun acquire() {
        while (true) {
            val waitMs = tryAcquireSlot()
            if (waitMs == 0L) return
            Timber.w("Rate limited, waiting %dms", waitMs)
            delay(waitMs.coerceIn(1_000, 10_000).milliseconds)
        }
    }

    private fun tryAcquireSlot(): Long {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            slots.removeAll { now - it > 60_000 }
            if (slots.size < maxPerMinute) {
                slots.add(now)
                return 0L
            }
            val waitMs = 60_000 - (now - slots.first())
            return waitMs
        }
    }
}
