package com.omni.orders.domain.service;

import java.math.BigDecimal;
import java.time.Instant;

public class ProcessPaymentCommand {

    private final String     mode;
    private final String     transactionId;
    private final BigDecimal amount;
    private final String     currency;
    private final Instant    paidAt;

    public ProcessPaymentCommand(String mode, String transactionId,
                                  BigDecimal amount, String currency,
                                  Instant paidAt) {
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
