package com.novabank.account.audit;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

/**
 * Uses the error the client receives as the audit reason, unless the service already gave a
 * more specific one. For validation errors only the field names are kept, never their values.
 */
@RestControllerAdvice
public class ProblemDetailAuditAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return ProblemDetail.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ProblemDetail pd) {
            String reason = pd.getDetail();
            if (pd.getProperties() != null && pd.getProperties().get("errors") instanceof Map<?, ?> errors
                    && !errors.isEmpty()) {
                reason = reason + ": " + String.join(", ", errors.keySet().stream().map(String::valueOf).toList());
            }
            Audit.reasonIfAbsent(reason);
        }
        return body;
    }
}
