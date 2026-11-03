package com.novabank.account.audit;

import java.time.Instant;
import java.util.Map;

/**
 * One audit event, sent to auth-service's POST /internal/audit. auth-service owns the central
 * audit log (NB_AUTH.AUDIT_LOG); this service keeps no audit table of its own.
 */
public record AuditEvent(String service, Instant createdAt, Long actorId, String actorRole, String actorEmail,
                         String action, String targetType, String targetId, Outcome outcome, Integer httpStatus,
                         String reason, Map<String, String> details, String correlationId, String sourceIp) {

    public enum Outcome { SUCCESS, FAILURE }

    /** Name this service reports under. auth-service only accepts "accounts" or "transactions". */
    public static final String SERVICE = "accounts";

    /** Applies the column limits of NB_AUTH.AUDIT_LOG so auth-service never rejects an event for length. */
    public AuditEvent {
        actorRole = Audit.truncate(actorRole, 10);
        actorEmail = Audit.truncate(actorEmail, 120);
        targetType = Audit.truncate(targetType, 20);
        targetId = Audit.truncate(targetId, 64);
        reason = Audit.truncate(reason, 200);
        correlationId = Audit.truncate(correlationId, 64);
        sourceIp = Audit.truncate(sourceIp, 200);
        details = details == null || details.isEmpty() ? null : Map.copyOf(details);
    }
}
