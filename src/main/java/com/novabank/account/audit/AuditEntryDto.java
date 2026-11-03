package com.novabank.account.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

public record AuditEntryDto(Long id, Instant occurredAt, String service, Long actorId, String actorRole,
                            String actorEmail, String action, String targetType, String targetId,
                            AuditLog.Outcome outcome, Integer httpStatus, String reason,
                            Map<String, String> details, String correlationId, String sourceIp) {

    static final String SERVICE = "accounts";
    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {
    };

    public static AuditEntryDto from(AuditLog e, ObjectMapper json) {
        return new AuditEntryDto(e.getId(), e.getOccurredAt(), SERVICE, e.getActorId(), e.getActorRole(),
                e.getActorEmail(), e.getAction(), e.getTargetType(), e.getTargetId(), e.getOutcome(),
                e.getHttpStatus(), e.getReason(), parse(e.getDetails(), json), e.getCorrelationId(), e.getSourceIp());
    }

    private static Map<String, String> parse(String details, ObjectMapper json) {
        if (details == null) {
            return Map.of();
        }
        try {
            return json.readValue(details, MAP);
        } catch (Exception e) {
            return Map.of("raw", details);
        }
    }
}
