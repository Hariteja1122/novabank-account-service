package com.novabank.account.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves audit rows in their own transaction, so a failed action (whose transaction rolled
 * back) is still recorded.
 */
@Component
public class AuditWriter {

    private final AuditLogRepository repo;

    public AuditWriter(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog entry) {
        repo.save(entry);
    }
}
