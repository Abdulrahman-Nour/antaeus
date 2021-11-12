package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import mu.KotlinLogging


class BillingService(private val dal: AntaeusDal, private val paymentProvider: PaymentProvider) {

    private fun chargeInvoice(invoice: Invoice) {
        for (i in 0..MAX_ATTEMPTS) {
            try {
                val isSuccessful = paymentProvider.charge(invoice)
                if (isSuccessful) {
                    logger.info { "invoice ${invoice.id} succeeded with amount paid ${invoice.amount}" }
                    dal.updateInvoice(invoice.copy(status = InvoiceStatus.PAID))
                } else {
                    logger.info { "invoice ${invoice.id} failed due to insufficient funds." }
                }
                break
            } catch (exception: CustomerNotFoundException) {
                logger.error(exception) { "invoice ${invoice.id} failed because customer ${invoice.customerId} doesn't exist. Aborting payment." }
                break
            } catch (exception: CurrencyMismatchException) {
                logger.error(exception) { "invoice ${invoice.id} failed because customer ${invoice.customerId} is using a different currency. Aborting payment." }
                break
            } catch (exception: NetworkException) {
                logger.error(exception) { "invoice ${invoice.id} failed because of a network error. ${if (i == MAX_ATTEMPTS - 1) "Aborting payment." else " retrying ${i + 1} / $MAX_ATTEMPTS"}" }
            }
        }

    }

    fun chargeInvoices(invoices: List<Invoice>) = invoices.forEach { chargeInvoice(it) }

    companion object {
        private val logger = KotlinLogging.logger {}

        // move this to a constants/configurations class/file ?
        private const val MAX_ATTEMPTS = 3

    }


}
