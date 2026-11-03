package com.novabank.account.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Audit trail of account-service: accounts opened by customers and admins, and admin status changes
 * (freeze, close, reactivate). ROLE_ADMIN at the gateway and here.
 * Reading the trail is itself not audited (only changes are).
 */
@RestController
@RequestMapping("/api/admin/audit/accounts")
public class AdminAuditController {

    private final AuditLogRepository repo;
    private final ObjectMapper json;

    public AdminAuditController(AuditLogRepository repo, ObjectMapper json) {
        this.repo = repo;
        this.json = json;
    }

    @GetMapping
    public Page<AuditEntryDto> search(@RequestParam(required = false) String action,
                                      @RequestParam(required = false) AuditLog.Outcome outcome,
                                      @RequestParam(required = false) Long actorId,
                                      @RequestParam(required = false) String targetId,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return repo.findAll(AuditSpecs.filter(action, outcome, actorId, targetId, q, from, to), pageable)
                .map(e -> AuditEntryDto.from(e, json));
    }
}
