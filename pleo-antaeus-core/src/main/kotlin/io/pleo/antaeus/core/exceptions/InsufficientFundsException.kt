package io.pleo.antaeus.core.exceptions

class InsufficientFundsException(invoiceId: Int, customerId: Int) : Throwable("Insufficient Funds for $invoiceId from $customerId ")
