package io.pleo.antaeus.core.exceptions

class InvoiceDoubleChargeException(invoiceId: Int) : Throwable("attempt to charge invoice $invoiceId twice")