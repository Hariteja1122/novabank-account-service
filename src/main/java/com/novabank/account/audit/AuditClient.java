package com.novabank.account.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Sends audit events to auth-service (POST /internal/audit), fire and forget.
 *
 * <p>The business action must never fail because of auditing, so {@link #send} never throws
 * and never blocks the request: the call runs on its own virtual thread with short timeouts,
 * and every failure (auth-service down, slow, 4xx, 5xx) is caught and logged as
 * AUDIT WRITE FAILED. The trade-off: an event whose delivery fails is lost, and only that log
 * line remains.
 */
@Component
public class AuditClient {

    private static final Logger log = LoggerFactory.getLogger(AuditClient.class);
    static final int TIMEOUT_MS = 2000;

    private final RestClient client;
    private final Executor executor;

    /** The constructor Spring uses (the other one is for tests). */
    @Autowired
    public AuditClient(RestClient.Builder builder,
                       @Value("${novabank.services.auth-url}") String authUrl,
                       @Value("${novabank.security.internal-api-key}") String internalKey) {
        this(builder.requestFactory(timeouts()), authUrl, internalKey, Executors.newVirtualThreadPerTaskExecutor());
    }

    /** Tests pass their own builder (for example bound to a mock server) and executor. */
    AuditClient(RestClient.Builder builder, String authUrl, String internalKey, Executor executor) {
        this.client = builder
                .baseUrl(authUrl)
                .defaultHeader("X-Internal-Api-Key", internalKey)
                .build();
        this.executor = executor;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        return factory;
    }

    /** Never throws. Returns immediately; delivery happens in the background. */
    public void send(AuditEvent event) {
        try {
            executor.execute(() -> deliver(event));
        } catch (RejectedExecutionException | NullPointerException ex) {
            failed(event, ex);
        }
    }

    void deliver(AuditEvent event) {
        try {
            client.post().uri("/internal/audit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            failed(event, ex);
        }
    }

    private static void failed(AuditEvent event, Exception ex) {
        log.error("AUDIT WRITE FAILED action={} actor={} target={}:{} outcome={} correlationId={}: {}",
                event == null ? null : event.action(), event == null ? null : event.actorId(),
                event == null ? null : event.targetType(), event == null ? null : event.targetId(),
                event == null ? null : event.outcome(), event == null ? null : event.correlationId(),
                ex.toString());
    }
}
