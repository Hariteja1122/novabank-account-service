package com.novabank.account.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novabank.account.common.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.Map;

/**
 * Writes one AUDIT_LOG row for every call to an {@link Audited} handler, after the response
 * is complete, so the row carries the final HTTP status. Runs for successes and failures,
 * including requests rejected by validation before the handler body runs.
 *
 * <p>What is stored: the caller from the JWT (or what the handler set through {@link Audit}),
 * the action and target, outcome, status, reason, whitelisted details, correlation id and
 * source address. Request bodies are never read here.
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditInterceptor.class);

    private final AuditWriter writer;
    private final ObjectMapper json;

    public AuditInterceptor(AuditWriter writer, ObjectMapper json) {
        this.writer = writer;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Audited audited = audited(handler);
        if (audited == null) {
            return true;
        }
        Audit.Context ctx = new Audit.Context();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            ctx.actorId = parseId(jwt.getSubject());
            ctx.actorRole = jwt.getClaimAsString("role");
            ctx.actorEmail = jwt.getClaimAsString("email");
        }
        if (StringUtils.hasText(audited.targetType())) {
            ctx.targetType = audited.targetType();
        }
        if (StringUtils.hasText(audited.targetParam())
                && request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) instanceof Map<?, ?> vars) {
            Object value = vars.get(audited.targetParam());
            ctx.targetId = value == null ? null : value.toString();
        }
        request.setAttribute(Audit.ATTRIBUTE, ctx);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        Audited audited = audited(handler);
        if (audited == null || !(request.getAttribute(Audit.ATTRIBUTE) instanceof Audit.Context ctx)) {
            return;
        }
        int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();
        AuditLog entry = new AuditLog();
        entry.setOccurredAt(Instant.now());
        entry.setActorId(ctx.actorId);
        entry.setActorRole(ctx.actorRole);
        entry.setActorEmail(ctx.actorEmail);
        entry.setAction(audited.action());
        entry.setTargetType(ctx.targetType);
        entry.setTargetId(ctx.targetId);
        entry.setOutcome(status < 400 ? AuditLog.Outcome.SUCCESS : AuditLog.Outcome.FAILURE);
        entry.setHttpStatus(status);
        entry.setReason(status < 400 ? null : ctx.reason);
        entry.setDetails(toJson(ctx.details));
        entry.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        entry.setSourceIp(sourceIp(request));
        try {
            writer.write(entry);
        } catch (RuntimeException writeFailure) {
            // The action already happened and the response is sent. Losing the row must be loud.
            log.error("AUDIT WRITE FAILED action={} actor={} target={}:{} status={}", entry.getAction(),
                    entry.getActorId(), entry.getTargetType(), entry.getTargetId(), status, writeFailure);
        }
    }

    private static Audited audited(Object handler) {
        return handler instanceof HandlerMethod hm ? hm.getMethodAnnotation(Audited.class) : null;
    }

    private String toJson(Map<String, String> details) {
        if (details.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** As reported by the proxies in front of us (gateway, Cloud Run). Informational, not proof. */
    private static String sourceIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return StringUtils.hasText(forwarded) ? forwarded.trim() : request.getRemoteAddr();
    }

    private static Long parseId(String subject) {
        try {
            return subject == null ? null : Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
