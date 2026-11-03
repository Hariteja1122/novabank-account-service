package com.novabank.account.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One state-changing action, successful or not. Rows are only ever inserted. */
@Entity
@Table(name = "AUDIT_LOG", schema = "NB_ACCT")
public class AuditLog {

    public enum Outcome { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "OCCURRED_AT", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "ACTOR_ID", updatable = false)
    private Long actorId;

    @Column(name = "ACTOR_ROLE", length = 10, updatable = false)
    private String actorRole;

    @Column(name = "ACTOR_EMAIL", length = 120, updatable = false)
    private String actorEmail;

    @Column(name = "ACTION", nullable = false, length = 40, updatable = false)
    private String action;

    @Column(name = "TARGET_TYPE", length = 20, updatable = false)
    private String targetType;

    @Column(name = "TARGET_ID", length = 64, updatable = false)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "OUTCOME", nullable = false, length = 10, updatable = false)
    private Outcome outcome;

    @Column(name = "HTTP_STATUS", updatable = false)
    private Integer httpStatus;

    @Column(name = "REASON", length = 200, updatable = false)
    private String reason;

    @Column(name = "DETAILS", length = 1000, updatable = false)
    private String details;

    @Column(name = "CORRELATION_ID", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "SOURCE_IP", length = 200, updatable = false)
    private String sourceIp;

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = Audit.truncate(actorRole, 10); }
    public String getActorEmail() { return actorEmail; }
    public void setActorEmail(String actorEmail) { this.actorEmail = Audit.truncate(actorEmail, 120); }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = Audit.truncate(targetType, 20); }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = Audit.truncate(targetId, 64); }
    public Outcome getOutcome() { return outcome; }
    public void setOutcome(Outcome outcome) { this.outcome = outcome; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = Audit.truncate(reason, 200); }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = Audit.truncate(details, 1000); }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = Audit.truncate(correlationId, 64); }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = Audit.truncate(sourceIp, 200); }
}
