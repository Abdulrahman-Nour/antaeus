
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import java.math.BigDecimal
import java.util.*
import kotlin.random.Random

// This will create all schemas and setup initial data
internal fun setupInitialData(dal: AntaeusDal) {
    val customers = (1..100).mapNotNull {
        dal.createCustomer(
            currency = Currency.values()[Random.nextInt(0, Currency.values().size)]
        )
    }

    customers.forEach { customer ->
        (1..10).forEach {
            dal.createInvoice(
                amount = Money(
                    value = BigDecimal(Random.nextDouble(10.0, 500.0)),
                    currency = customer.currency
                ),
                customer = customer,
                status = if (it == 1) InvoiceStatus.PENDING else InvoiceStatus.PAID
            )
        }
    }
}

// This is the mocked instance of the payment provider
internal fun getPaymentProvider(): PaymentProvider {
    return object : PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
                return Random.nextBoolean()
        }
    }
}

internal val millisUntilNextMonth: Long
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