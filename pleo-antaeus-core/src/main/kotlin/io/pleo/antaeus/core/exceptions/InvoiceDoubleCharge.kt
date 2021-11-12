package io.pleo.antaeus.core.exceptions

class InvoiceDoubleCharge(invoiceId: Int) : Throwable("attempt to charge invoice $invoiceId twice")