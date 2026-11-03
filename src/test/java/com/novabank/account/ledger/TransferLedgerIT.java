package com.novabank.account.ledger;

import com.novabank.account.account.AccountDtos.OpenAccountRequest;
import com.novabank.account.account.AccountDtos.TransferRequest;
import com.novabank.account.account.AccountRepository;
import com.novabank.account.account.AccountService;
import com.novabank.account.account.AccountType;
import com.novabank.account.common.ApiException;
import com.novabank.account.ledger.LedgerEntry.Outcome;
import com.novabank.account.support.Concurrently;
import com.novabank.account.support.Db2IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ledger is what reconciliation trusts, so these run against real Db2: the primary
 * key on REFERENCE must make "money moved" and "voided" mutually exclusive.
 */
class TransferLedgerIT extends Db2IntegrationTest {

    private static final AtomicLong CUSTOMER_IDS = new AtomicLong(60_000);

    @Autowired
    AccountService service;

    @Autowired
    AccountRepository repo;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void appliedTransferIsRecordedAndCannotBeVoided() {
        String from = open("100.00");
        String to = open("0.00");

        service.transfer(new TransferRequest(from, to, new BigDecimal("40.00"), null, "IT-LEDGER-A1"));

        assertThat(outcomeOf("IT-LEDGER-A1")).isEqualTo("APPLIED");
        assertThat(service.voidUnlessApplied("IT-LEDGER-A1").getOutcome()).isEqualTo(Outcome.APPLIED);
        assertThat(balance(from)).isEqualByComparingTo("60.00");
    }

    @Test
    void retryWithTheSameReferenceDoesNotMoveMoneyTwice() {
        String from = open("100.00");
        String to = open("0.00");
        var req = new TransferRequest(from, to, new BigDecimal("40.00"), null, "IT-LEDGER-R1");

        service.transfer(req);
        var replay = service.transfer(req);

        assertThat(replay.from().balance()).isEqualByComparingTo("60.00");
        assertThat(balance(from)).isEqualByComparingTo("60.00");
        assertThat(balance(to)).isEqualByComparingTo("40.00");
    }

    @Test
    void voidedReferenceIsRejectedAndMovesNoMoney() {
        String from = open("100.00");
        String to = open("0.00");

        assertThat(service.voidUnlessApplied("IT-LEDGER-V1").getOutcome()).isEqualTo(Outcome.VOIDED);
        assertThatThrownBy(() -> service.transfer(new TransferRequest(from, to, new BigDecimal("40.00"), null, "IT-LEDGER-V1")))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(balance(from)).isEqualByComparingTo("100.00");
        assertThat(balance(to)).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectedTransferLeavesNoLedgerRowSoItCanStillBeVoided() {
        String from = open("10.00");
        String to = open("0.00");

        assertThatThrownBy(() -> service.transfer(new TransferRequest(from, to, new BigDecimal("40.00"), null, "IT-LEDGER-F1")))
                .hasMessage("Insufficient funds");

        assertThat(outcomeOf("IT-LEDGER-F1")).isNull();
        assertThat(service.voidUnlessApplied("IT-LEDGER-F1").getOutcome()).isEqualTo(Outcome.VOIDED);
    }

    @Test
    void racingTransfersAndVoidsAgreeWithTheBalances() throws Exception {
        // Each reference gets a transfer and a void at the same moment, the situation
        // reconciliation creates when it fires while a slow request is still in flight.
        String from = open("100.00");
        String to = open("0.00");
        int references = 20;

        List<Throwable> outcomes = Concurrently.run(references * 2, i -> {
            String ref = "IT-LEDGER-X" + (i / 2);
            return i % 2 == 0
                    ? () -> service.transfer(new TransferRequest(from, to, new BigDecimal("1.00"), null, ref))
                    : () -> service.voidUnlessApplied(ref);
        });

        assertThat(outcomes).as("only clean 409s from the losing side").allSatisfy(t -> {
            if (t != null) {
                assertThat(t).isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
            }
        });
        Integer applied = jdbc.queryForObject(
                "SELECT COUNT(*) FROM NB_ACCT.TRANSFER_LEDGER WHERE REFERENCE LIKE 'IT-LEDGER-X%' AND OUTCOME = 'APPLIED'",
                Integer.class);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM NB_ACCT.TRANSFER_LEDGER WHERE REFERENCE LIKE 'IT-LEDGER-X%'", Integer.class);
        assertThat(total).as("every reference settled exactly once").isEqualTo(references);
        assertThat(balance(to)).as("money moved for exactly the APPLIED references")
                .isEqualByComparingTo(new BigDecimal(applied).setScale(2));
        assertThat(balance(from)).isEqualByComparingTo(new BigDecimal(100 - applied).setScale(2));
    }

    private String open(String deposit) {
        long customerId = CUSTOMER_IDS.incrementAndGet();
        return service.open(new OpenAccountRequest(customerId, "Ledger IT " + customerId, AccountType.CHECKING,
                new BigDecimal(deposit))).accountNumber();
    }

    private BigDecimal balance(String accountNumber) {
        return repo.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }

    private String outcomeOf(String reference) {
        return jdbc.query("SELECT OUTCOME FROM NB_ACCT.TRANSFER_LEDGER WHERE REFERENCE = ?",
                rs -> rs.next() ? rs.getString(1) : null, reference);
    }
}
