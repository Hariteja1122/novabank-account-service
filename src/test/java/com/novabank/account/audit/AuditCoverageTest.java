package com.novabank.account.audit;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every endpoint that can change something must be audited, and reads must not be.
 * A new POST/PUT/PATCH/DELETE handler under /api without @Audited fails this test.
 */
class AuditCoverageTest {

    private static final Set<RequestMethod> WRITES =
            Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    @Test
    void everyWriteEndpointIsAuditedAndNoReadIs() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> unaudited = new ArrayList<>();
        List<String> auditedReads = new ArrayList<>();
        int writes = 0;
        for (var bean : scanner.findCandidateComponents("com.novabank")) {
            Class<?> type = Class.forName(bean.getBeanClassName());
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            if (base == null || Arrays.stream(base.value()).noneMatch(p -> p.startsWith("/api/"))) {
                continue; // /internal/** is audited by the calling service
            }
            for (Method m : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                boolean write = Arrays.stream(mapping.method()).anyMatch(WRITES::contains);
                boolean audited = m.isAnnotationPresent(Audited.class);
                if (write) {
                    writes++;
                    if (!audited) {
                        unaudited.add(type.getSimpleName() + "#" + m.getName());
                    }
                } else if (audited) {
                    auditedReads.add(type.getSimpleName() + "#" + m.getName());
                }
            }
        }
        assertThat(writes).as("write endpoints found").isPositive();
        assertThat(unaudited).as("write endpoints without @Audited").isEmpty();
        assertThat(auditedReads).as("read endpoints must not be audited").isEmpty();
    }
}
