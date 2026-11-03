package com.novabank.account.account;

import com.novabank.account.account.AccountDtos.OpenAccountRequest;
import com.novabank.account.account.AccountDtos.TransferRequest;
import com.novabank.account.common.ApiException;
import com.novabank.account.support.Concurrently;
import com.novabank.account.support.Db2IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Money movement against a real Db2: row locks, the @Version column and the
 * CHECK (BALANCE >= 0) constraint only behave for real on the real database.
 */
@AutoConfigureMockMvc
class AccountTransferIT extends Db2IntegrationTest {

    /** Each test uses fresh customers so the 4-accounts-per-customer rule never interferes. */
    private static final AtomicLong CUSTOMER_IDS = new AtomicLong(50_000);

    @Autowired
    AccountService service;

    @Autowired
    AccountRepository repo;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void transferMovesMoneyAndCommitsBothBalances() {
        Account from = open("500.00");
        Account to = open("10.00");

        var result = service.transfer(new TransferRequest(from.getAccountNumber(), to.getAccountNumber(),
                new BigDecimal("125.50"), from.getCustomerId(), "IT-TRANSFER-1"));

        assertThat(result.from().balance()).isEqualByComparingTo("374.50");
        assertThat(result.to().balance()).isEqualByComparingTo("135.50");
        Account fromAfter = reload(from);
        Account toAfter = reload(to);
        assertThat(fromAfter.getBalance()).isEqualByComparingTo("374.50");
        assertThat(toAfter.getBalance()).isEqualByComparingTo("135.50");
        assertThat(fromAfter.getVersion()).isEqualTo(from.getVersion() + 1);
        assertThat(toAfter.getVersion()).isEqualTo(to.getVersion() + 1);
    }

    @Test
    void insufficientFundsIsRejectedAndNothingIsWritten() {
        Account from = open("20.00");
        Account to = open("0.00");

        assertThatThrownBy(() -> service.transfer(new TransferRequest(from.getAccountNumber(), to.getAccountNumber(),
                new BigDecimal("20.01"), from.getCustomerId(), "IT-INSUFFICIENT-1")))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo("Insufficient funds");
                });

        assertThat(reload(from).getBalance()).isEqualByComparingTo("20.00");
        assertThat(reload(to).getBalance()).isEqualByComparingTo("0.00");
        assertThat(reload(from).getVersion()).isEqualTo(from.getVersion());
    }

    @Test
    void insufficientFundsReachesCallersAsProblemDetail() throws Exception {
        // The path transaction-service uses: internal API key, validation, ProblemDetail mapping.
        Account from = open("5.00");
        Account to = open("0.00");

        mvc.perform(post("/internal/accounts/transfer")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccount":"%s","toAccount":"%s","amount":5.01,"requestedBy":%d,"reference":"IT-HTTP-1"}
                                """.formatted(from.getAccountNumber(), to.getAccountNumber(), from.getCustomerId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Insufficient funds"));

        assertThat(reload(from).getBalance()).isEqualByComparingTo("5.00");
    }

    @Test
    void databaseRejectsNegativeBalanceEvenIfTheServiceCheckIsBypassed() {
        Account account = open("1.00");

        assertThatThrownBy(() -> jdbc.update("UPDATE NB_ACCT.ACCOUNTS SET BALANCE = BALANCE - 1.01 WHERE ACCOUNT_NUMBER = ?",
                account.getAccountNumber()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(reload(account).getBalance()).isEqualByComparingTo("1.00");
    }

    @Test
    void concurrentTransfersFromOneAccountNeverOverdrawIt() throws Exception {
        Account from = open("100.00");
        Account to = open("0.00");
        int attempts = 25; // only 10 of these can be funded

        List<Throwable> outcomes = Concurrently.run(attempts, i -> () -> service.transfer(new TransferRequest(
                from.getAccountNumber(), to.getAccountNumber(), new BigDecimal("10.00"),
                from.getCustomerId(), "IT-RACE-" + i)));

        List<Throwable> failures = outcomes.stream().filter(t -> t != null).toList();
        assertThat(outcomes.size() - failures.size()).as("successful transfers").isEqualTo(10);
        assertThat(failures).as("every failure is a clean business rejection").allSatisfy(t ->
                assertThat(t).isInstanceOf(ApiException.class).hasMessage("Insufficient funds"));
        assertThat(reload(from).getBalance()).isEqualByComparingTo("0.00");
        assertThat(reload(to).getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void opposingConcurrentTransfersDoNotDeadlockOrLoseMoney() throws Exception {
        Account a = open("1000.00");
        Account b = open("1000.00");
        int attempts = 40; // half A->B for 7.00, half B->A for 3.00

        List<Throwable> outcomes = Concurrently.run(attempts, i -> {
            boolean aToB = i % 2 == 0;
            Account from = aToB ? a : b;
            Account to = aToB ? b : a;
            BigDecimal amount = new BigDecimal(aToB ? "7.00" : "3.00");
            return () -> service.transfer(new TransferRequest(from.getAccountNumber(), to.getAccountNumber(),
                    amount, from.getCustomerId(), "IT-OPPOSING-" + i));
        });

        assertThat(outcomes).as("no deadlocks, lock timeouts or optimistic lock failures").containsOnlyNulls();
        assertThat(reload(a).getBalance()).isEqualByComparingTo("920.00");  // 1000 - 20*7 + 20*3
        assertThat(reload(b).getBalance()).isEqualByComparingTo("1080.00"); // 1000 + 20*7 - 20*3
    }

    private Account open(String initialDeposit) {
        long customerId = CUSTOMER_IDS.incrementAndGet();
        var dto = service.open(new OpenAccountRequest(customerId, "IT Customer " + customerId,
                AccountType.CHECKING, new BigDecimal(initialDeposit)));
        return repo.findByAccountNumber(dto.accountNumber()).orElseThrow();
    }

    private Account reload(Account account) {
        return repo.findByAccountNumber(account.getAccountNumber()).orElseThrow();
    }
}
