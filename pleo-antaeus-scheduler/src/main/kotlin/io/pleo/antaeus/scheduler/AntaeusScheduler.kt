/*
    Configures the rest app along with basic exception handling and URL endpoints.
 */

package io.pleo.antaeus.scheduler

import kotlinx.coroutines.*
import java.util.*

class AntaeusScheduler {

    // should add other time units as needed, nothing else needed as of now
    enum class TimeUnit {
        MONTH
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleEvery(timeUnit: TimeUnit, job: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.Default) {
            while (true) {
                when (timeUnit) {
                    TimeUnit.MONTH -> delay(millisUntilNextMonth)
                }
                job.invoke()
            }
        }

    }

    private val millisUntilNextMonth: Long
        get() = timeNextMonthInMillis - timeNowInMillis

    private val timeNowInMillis: Long
        get() = System.currentTimeMillis()

    private val timeNextMonthInMillis: Long
        get() = Calendar.getInstance().apply {
            // remove time component, so that invoices are charged at the start of the day
            set(Calendar.HOUR_OF_DAY, getActualMinimum(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, getActualMinimum(Calendar.MINUTE))
            set(Calendar.SECOND, getActualMinimum(Calendar.SECOND))
            set(Calendar.MILLISECOND, getActualMinimum(Calendar.MILLISECOND))

            // set calendar instance to the first day of next month
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, get(Calendar.MONTH) + 1)
        }.timeInMillis
}
