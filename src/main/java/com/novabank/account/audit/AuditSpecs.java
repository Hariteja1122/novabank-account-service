package com.novabank.account.audit;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Optional audit filters as criteria predicates (Db2 rejects untyped "? IS NULL"). */
public final class AuditSpecs {

    private AuditSpecs() {
    }

    public static Specification<AuditLog> filter(String action, AuditLog.Outcome outcome, Long actorId,
                                                 String targetId, String q, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (StringUtils.hasText(action)) {
                p.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            }
            if (outcome != null) {
                p.add(cb.equal(root.get("outcome"), outcome));
            }
            if (actorId != null) {
                p.add(cb.equal(root.get("actorId"), actorId));
            }
            if (StringUtils.hasText(targetId)) {
                p.add(cb.equal(root.get("targetId"), targetId.trim()));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("actorEmail")), like),
                        cb.like(cb.lower(root.<String>get("targetId")), like),
                        cb.like(cb.lower(root.<String>get("correlationId")), like)));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("occurredAt"), to));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
