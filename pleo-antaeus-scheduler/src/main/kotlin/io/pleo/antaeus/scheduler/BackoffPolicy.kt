package io.pleo.antaeus.scheduler

class BackoffPolicy(
        private val maxRetries: Int,
        private val delayMs: Int
) : Iterator<Int> {

    private var attempt = 1

    override fun hasNext(): Boolean = attempt <= maxRetries

    override fun next(): Int {
        Thread.sleep(Math.scalb(delayMs.toDouble(), attempt - 1).toLong())
        return attempt++
    }
}