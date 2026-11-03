package com.novabank.account.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.novabank.account.account.AccountDtos.AccountDto;
import com.novabank.account.account.AccountService;
import com.novabank.account.account.AccountStatus;
import com.novabank.account.account.AccountType;
import com.novabank.account.admin.AdminAccountController;
import com.novabank.account.account.AccountRepository;
import com.novabank.account.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auditing must never fail, block or roll back the business action. */
class AuditClientTest {

    /** Runs the delivery on the calling thread, so the test sees its outcome. */
    private static final Executor SAME_THREAD = Runnable::run;

    private static AuditEvent event() {
        return new AuditEvent(AuditEvent.SERVICE, Instant.now(), 1L, "ADMIN", "admin@novabank.dev",
                "ACCOUNT_STATUS_CHANGE", "ACCOUNT", "100245180031", AuditEvent.Outcome.SUCCESS, 200, null,
                Map.of("from", "ACTIVE", "to", "FROZEN"), "c-1", "203.0.113.7");
    }

    private static RestClient.Builder jsonBuilder() {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        return RestClient.builder().messageConverters(c -> {
            c.clear();
            c.add(new MappingJackson2HttpMessageConverter(json));
        });
    }

    @Test
    void springCanCreateTheClient() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "novabank.services.auth-url", "http://auth", "novabank.security.internal-api-key", "k")));
            ctx.registerBean(RestClient.Builder.class, (java.util.function.Supplier<RestClient.Builder>) RestClient::builder);
            ctx.register(AuditClient.class);
            ctx.refresh();
            assertThat(ctx.getBean(AuditClient.class)).isNotNull();
        }
    }

    @Test
    void sendsTheEventWithTheInternalKey() {
        RestClient.Builder builder = jsonBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://auth/internal/audit"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "k"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.service").value("accounts"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.action").value("ACCOUNT_STATUS_CHANGE"))
                .andExpect(MockRestRequestMatchers.jsonPath("$.details.to").value("FROZEN"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        new AuditClient(builder, "http://auth", "k", SAME_THREAD).send(event());

        server.verify();
    }

    @Test
    void anErrorFromAuthServiceIsSwallowed() {
        RestClient.Builder builder = jsonBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://auth/internal/audit")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatCode(() -> new AuditClient(builder, "http://auth", "k", SAME_THREAD).send(event()))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnreachableAuthServiceIsSwallowed() {
        AuditClient client = new AuditClient(RestClient.builder(), "http://localhost:1", "k", SAME_THREAD);

        assertThatCode(() -> client.send(event())).doesNotThrowAnyException();
    }

    @Test
    void aRejectedBackgroundTaskIsSwallowed() {
        Executor full = task -> {
            throw new java.util.concurrent.RejectedExecutionException("shutting down");
        };
        AuditClient client = new AuditClient(RestClient.builder(), "http://auth", "k", full);

        assertThatCode(() -> client.send(event())).doesNotThrowAnyException();
    }

    @Test
    void freezingAnAccountSucceedsWhileAuthServiceIsDown() throws Exception {
        AccountService service = mock(AccountService.class);
        when(service.changeStatus(eq("100245180031"), eq(AccountStatus.FROZEN))).thenReturn(new AccountDto(
                5L, "100245180031", 3L, "John Carter", AccountType.CHECKING, new BigDecimal("12.00"), "USD",
                AccountStatus.FROZEN, Instant.now()));
        AuditClient down = new AuditClient(RestClient.builder(), "http://localhost:1", "k", SAME_THREAD);
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new AdminAccountController(service, mock(AccountRepository.class)))
                .setControllerAdvice(new GlobalExceptionHandler(), new ProblemDetailAuditAdvice())
                .addMappedInterceptors(new String[]{"/api/**"}, new AuditInterceptor(down))
                .build();

        mvc.perform(patch("/api/admin/accounts/100245180031/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("FROZEN"));

        verify(service).changeStatus("100245180031", AccountStatus.FROZEN);
    }
}
