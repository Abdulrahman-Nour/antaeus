package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.InsufficientFundsException
import io.pleo.antaeus.core.exceptions.InvoiceDoubleChargeException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.random.Random

class BillingServiceTest {

    private val dal = mockk<AntaeusDal> {
        val pendingInvoice = Invoice(1, Random.nextInt(), amount = Money(BigDecimal.valueOf(Random.nextLong(500)), Currency.GBP ), InvoiceStatus.PENDING)
        val paidInvoice = Invoice(2, Random.nextInt(), amount = Money(BigDecimal.valueOf(Random.nextLong(500)), Currency.GBP ), InvoiceStatus.PAID)
        every { fetchInvoice(1) } returns pendingInvoice
        every { fetchInvoice(2) } returns paidInvoice
    }

    private val successBillingService = BillingService(dal = dal, paymentProvider = object: PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
            return true
        }
    })
    private val insufficientFundsBillingService = BillingService(dal = dal, paymentProvider = object: PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
            return false
        }
    })
    private val currencyMismatchBillingService = BillingService(dal = dal, paymentProvider = object: PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
            throw CurrencyMismatchException(invoiceId = invoice.id, customerId = invoice.customerId)
        }
    })

    private val terribleNetworkBillingService = BillingService(dal = dal, paymentProvider = object: PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
            throw NetworkException()
        }
    })

    @Test
    fun `will throw if invoice already paid`() {
        assertThrows<InvoiceDoubleChargeException> {
            successBillingService.bill(dal.fetchInvoice(2)!!)
        }
    }

    @Test
    fun `will throw if insufficient funds`() {
        assertThrows<InsufficientFundsException> {
            insufficientFundsBillingService.bill(dal.fetchInvoice(1)!!)
        }
    }

    @Test
    fun `will throw if currency mismatch`() {
        assertThrows<CurrencyMismatchException> {
            currencyMismatchBillingService.bill(dal.fetchInvoice(1)!!)
        }
    }

    @Test
    fun `should eventually throw network exception`() {
        assertThrows<NetworkException> {
            terribleNetworkBillingService.bill(dal.fetchInvoice(1)!!)
        }
    }
}