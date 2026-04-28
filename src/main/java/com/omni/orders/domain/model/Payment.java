package com.omni.orders.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable payment snapshot attached to an order once it transitions to PAID.
 * Preserved even if the order is later cancelled — required for reconciliation.
 */
public class Payment {

    private final String     mode;
    private final String     transactionId;
    private final BigDecimal amount;
    private final String     currency;
    private final Instant    paidAt;

    public Payment(String mode, String transactionId,
                   BigDecimal amount, String currency, Instant paidAt) {
        if (mode          == null || mode.isBlank())
            throw new IllegalArgumentException("mode is required");
        if (transactionId == null || transactionId.isBlank())
            throw new IllegalArgumentException("transactionId is required");
        if (amount        == null)
            throw new IllegalArgumentException("amount is required");
        if (currency      == null || currency.isBlank())
            throw new IllegalArgumentException("currency is required");
        if (paidAt        == null)
            throw new IllegalArgumentException("paidAt is required");

        this.mode          = mode;
        this.transactionId = transactionId;
        this.amount        = amount;
        this.currency      = currency;
        this.paidAt        = paidAt;
    }

    public String     getMode()          { return mode; }
    public String     getTransactionId() { return transactionId; }
    public BigDecimal getAmount()        { return amount; }
    public String     getCurrency()      { return currency; }
    public Instant    getPaidAt()        { return paidAt; }
}
