package com.novabank.account.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method that changes state. {@link AuditInterceptor} writes one
 * AUDIT_LOG row per call, whether it succeeds or fails. Every POST, PUT, PATCH and DELETE
 * handler under /api must carry it (AuditCoverageTest checks this). Reads are never audited.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    /** What happened, e.g. USER_STATUS_CHANGE. Stable: admins filter on it. */
    String action();

    /** Kind of thing acted on, e.g. USER. Empty when there is none. */
    String targetType() default "";

    /** Path variable that holds the target id. The handler can also set it with {@link Audit#target}. */
    String targetParam() default "";
}
