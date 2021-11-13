package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InsufficientFundsException
import io.pleo.antaeus.core.exceptions.InvoiceDoubleChargeException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.scheduler.BackoffPolicy
import kotlinx.coroutines.*
import mu.KotlinLogging

class BillingService(private val dal: AntaeusDal, private val paymentProvider: PaymentProvider) {

    suspend fun chargeInvoice(invoice: Invoice): Job {
        return coroutineScope { // launching a job per invoice to allow for multithreading.
            launch(Dispatchers.IO + createExceptionHandler(invoice)) { bill(invoice) }
        }
    }

    suspend fun chargeInvoices(invoices: List<Invoice>) = invoices.map { invoice -> chargeInvoice(invoice) }

    fun bill(invoice: Invoice) {
        // this check is added for the manual billing API to avoid charging the same invoice more than once
        if (invoice.status == InvoiceStatus.PAID) throw InvoiceDoubleChargeException(invoice.id)

        val backoffPolicy = BackoffPolicy(MAX_ATTEMPTS, 1000)
        for (attempt in backoffPolicy) {
            try {
                if (paymentProvider.charge(invoice)) {
                    logger.info { "invoice ${invoice.id} succeeded with amount ${invoice.amount} paid." }

                    // committing each invoice individually might slow things down
                    // but will avoid certain types of errors.
                    // TODO look into committing changes to db into batches (maybe use kotlin channels ?)
                    dal.updateInvoice(invoice.copy(status = InvoiceStatus.PAID))

                    break
                } else throw InsufficientFundsException(invoice.id, invoice.customerId)
            } catch (exception: NetworkException) {
                if (!backoffPolicy.hasNext()) throw exception // throw to outer scope / give up on retrying
                else logger.error(exception) { "invoice ${invoice.id} failed because of a network error. retrying $attempt / $MAX_ATTEMPTS" }
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
                logger.error(exception) { "Invoice ${invoice.id} failed because of a network error. Aborting payment." }
            is InvoiceDoubleChargeException ->
                logger.error(exception) { "Attempt to charge invoice ${invoice.id} more than once."}
            else ->
                logger.error(exception) { "Unexpected error." }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        // move this to a constants/configurations class/file ?
        private const val MAX_ATTEMPTS = 5

    }


}
