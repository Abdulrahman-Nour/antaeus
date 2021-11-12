package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InsufficientFundsException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import mu.KotlinLogging

class BillingService(private val dal: AntaeusDal, private val paymentProvider: PaymentProvider) {

    private fun chargeInvoice(invoice: Invoice) {
        val attempts = 1..MAX_ATTEMPTS
        for (attempt in attempts) {
            try {
                if (paymentProvider.charge(invoice)) {
                    logger.info { "invoice ${invoice.id} succeeded with amount ${invoice.amount} paid." }

                    // committing each invoice individually might slow things down
                    // but will avoid certain errors
                    // TODO look into committing changes to db into batches (use kotlin channels ?)
                    dal.updateInvoice(invoice.copy(status = InvoiceStatus.PAID))

                    break
                } else throw InsufficientFundsException(invoice.id, invoice.customerId)
            } catch (exception: NetworkException) {
                // we can implement exponential backoff, instead of trying a fixed amount of time.
                if (attempt == attempts.last) throw exception // throw to outer scope
                else logger.error(exception) { "invoice ${invoice.id} failed because of a network error. retrying $attempt / $MAX_ATTEMPTS"}
            }
        }
    }

    suspend fun chargeInvoices(invoices: List<Invoice>) {
        coroutineScope {
            invoices.forEach { invoice ->
                // launching a job per invoice to allow for multithreading.
                launch(Dispatchers.IO + createExceptionHandler(invoice)) {
                    chargeInvoice(invoice)
                }
            }
        }
    }

    private fun createExceptionHandler(invoice: Invoice) = CoroutineExceptionHandler { _, exception ->
        when (exception) {
            is CustomerNotFoundException ->
                logger.error(exception) { "Invoice ${invoice.id} failed because customer ${invoice.customerId} doesn't exist. Aborting payment." }
            is CurrencyMismatchException ->
                logger.error(exception) { "Invoice ${invoice.id} failed because customer ${invoice.customerId} is using a different currency. Aborting payment." }
            is InsufficientFundsException ->
                logger.error(exception) { "Invoice ${invoice.id} failed due to insufficient funds." }
            is NetworkException ->
                logger.error(exception) { "Invoice ${invoice.id} failed because of a network error. Aborting payment."}
            else ->
                logger.error(exception) { "Unexpected error." }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        // move this to a constants/configurations class/file ?
        private const val MAX_ATTEMPTS = 3

    }


}
