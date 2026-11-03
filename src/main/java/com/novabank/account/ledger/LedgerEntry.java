package com.novabank.account.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Final word on a transaction-service reference: did this service move the money or not. */
@Entity
@Table(name = "TRANSFER_LEDGER", schema = "NB_ACCT")
public class LedgerEntry {

    public enum Outcome { APPLIED, VOIDED }

    @Id
    @Column(name = "REFERENCE", length = 24)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME", nullable = false, updatable = false, length = 10)
    private Outcome outcome;

    @Column(name = "RECORDED_AT", nullable = false, updatable = false)
    private Instant recordedAt;

    protected LedgerEntry() {
    }

    LedgerEntry(String reference, Outcome outcome) {
        this.reference = reference;
        this.outcome = outcome;
        this.recordedAt = Instant.now();
    }

    public String getReference() { return reference; }
    public Outcome getOutcome() { return outcome; }
    public Instant getRecordedAt() { return recordedAt; }
}
