package com.novabank.account.audit;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets service code add facts to the audit row of the current request. Every call is a
 * no-op outside an {@link Audited} request (unit tests, scheduled jobs).
 *
 * <p>Only what is passed here, plus the caller identity and HTTP outcome, is stored. Request
 * bodies are never stored, so a password can only reach the audit log if someone passes it
 * to one of these methods. Never pass a password, hash, token or other secret.
 */
public final class Audit {

    static final String ATTRIBUTE = Audit.class.getName() + ".context";
    static final int MAX_DETAILS = 8;
    static final int MAX_VALUE_LENGTH = 120;

    private Audit() {
    }

    /** The authenticated caller, for endpoints that establish who the caller is (login, register). */
    public static void actor(Long id, String role, String email) {
        Context ctx = current();
        if (ctx != null) {
            ctx.actorId = id;
            ctx.actorRole = role;
            ctx.actorEmail = email;
        }
    }

    /** The email a caller tried to sign in with. Unverified: it is whatever was typed. */
    public static void attemptedEmail(String email) {
        Context ctx = current();
        if (ctx != null) {
            ctx.actorEmail = email;
        }
    }

    public static void target(String type, Object id) {
        Context ctx = current();
        if (ctx != null) {
            ctx.targetType = type;
            ctx.targetId = id == null ? null : String.valueOf(id);
        }
    }

    /** A non-secret fact about the change, such as the old and new status. */
    public static void detail(String key, Object value) {
        Context ctx = current();
        if (ctx != null && (ctx.details.size() < MAX_DETAILS || ctx.details.containsKey(key))) {
            ctx.details.put(key, truncate(value == null ? null : String.valueOf(value), MAX_VALUE_LENGTH));
        }
    }

    /**
     * Why the action failed, for admins. It can be more specific than the message the client
     * sees (for example "Unknown email" behind a generic "Invalid email or password").
     */
    public static void reason(String reason) {
        Context ctx = current();
        if (ctx != null) {
            ctx.reason = reason;
        }
    }

    /** Sets the reason only if service code has not already given a more specific one. */
    static void reasonIfAbsent(String reason) {
        Context ctx = current();
        if (ctx != null && ctx.reason == null) {
            ctx.reason = reason;
        }
    }

    static Context current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : (Context) attrs.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** Facts gathered while one audited request runs. */
    static final class Context {
        Long actorId;
        String actorRole;
        String actorEmail;
        String targetType;
        String targetId;
        String reason;
        final Map<String, String> details = new LinkedHashMap<>();
    }
}
