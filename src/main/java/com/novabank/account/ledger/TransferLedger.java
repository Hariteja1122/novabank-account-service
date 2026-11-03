package com.novabank.account.ledger;

import com.novabank.account.common.ApiException;
import com.novabank.account.ledger.LedgerEntry.Outcome;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Writes ledger rows with a plain INSERT (persist + flush), never a merge, so the primary
 * key on REFERENCE decides between APPLIED and VOIDED. Callers run inside their own
 * transaction; if the insert loses, that whole transaction rolls back.
 */
@Component
public class TransferLedger {

    private final EntityManager em;

    public TransferLedger(EntityManager em) {
        this.em = em;
    }

    public Optional<LedgerEntry> find(String reference) {
        return Optional.ofNullable(em.find(LedgerEntry.class, reference));
    }

    /**
     * Records the outcome or fails with 409 if another request already settled this
     * reference (including one still in flight: Db2 waits for it to commit or roll back).
     */
    public LedgerEntry record(String reference, Outcome outcome) {
        LedgerEntry entry = new LedgerEntry(reference, outcome);
        try {
            em.persist(entry);
            em.flush();
        } catch (EntityExistsException | ConstraintViolationException ex) {
            throw alreadySettled(reference);
        } catch (PersistenceException ex) {
            if (ex.getCause() instanceof ConstraintViolationException) {
                throw alreadySettled(reference);
            }
            throw ex;
        }
        return entry;
    }

    private static ApiException alreadySettled(String reference) {
        return new ApiException(HttpStatus.CONFLICT, "Reference " + reference + " was already settled");
    }
}
