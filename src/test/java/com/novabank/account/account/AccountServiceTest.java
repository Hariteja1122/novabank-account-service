package com.novabank.account.account;

import com.novabank.account.account.AccountDtos.DepositRequest;
import com.novabank.account.account.AccountDtos.TransferRequest;
import com.novabank.account.common.ApiException;
import com.novabank.account.ledger.LedgerEntry;
import com.novabank.account.ledger.LedgerEntry.Outcome;
import com.novabank.account.ledger.TransferLedger;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final AccountRepository repo = mock(AccountRepository.class);
    private final TransferLedger ledger = mock(TransferLedger.class);
    private final AccountService service = new AccountService(repo, new AccountNumberGenerator(), ledger);

    private Account account(String number, long customerId, String balance, AccountStatus status) {
        Account a = new Account();
        a.setAccountNumber(number);
        a.setCustomerId(customerId);
        a.setCustomerName("Test");
        a.setAccountType(AccountType.CHECKING);
        a.setBalance(new BigDecimal(balance));
        a.setStatus(status);
        when(repo.lockByAccountNumber(number)).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void transferMovesMoneyAndLocksInAccountNumberOrder() {
        Account from = account("100245180099", 3L, "500.00", AccountStatus.ACTIVE);
        Account to = account("100245180031", 4L, "10.00", AccountStatus.ACTIVE);

        var result = service.transfer(new TransferRequest("100245180099", "100245180031",
                new BigDecimal("125.50"), 3L, "NB0000000001"));

        assertThat(from.getBalance()).isEqualByComparingTo("374.50");
        assertThat(to.getBalance()).isEqualByComparingTo("135.50");
        assertThat(result.from().balance()).isEqualByComparingTo("374.50");

        InOrder order = inOrder(repo);
        order.verify(repo).lockByAccountNumber("100245180031");
        order.verify(repo).lockByAccountNumber("100245180099");
    }

    @Test
    void insufficientFundsIsRejectedWithoutChangingBalances() {
        Account from = account("100245180031", 3L, "20.00", AccountStatus.ACTIVE);
        Account to = account("100245180049", 3L, "0.00", AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.transfer(new TransferRequest("100245180031", "100245180049",
                new BigDecimal("20.01"), 3L, "NB0000000002")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Insufficient funds");
        assertThat(from.getBalance()).isEqualByComparingTo("20.00");
        assertThat(to.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void customerCannotSpendSomeoneElsesMoney() {
        account("100245180031", 3L, "900.00", AccountStatus.ACTIVE);
        account("100245180057", 4L, "0.00", AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.transfer(new TransferRequest("100245180031", "100245180057",
                new BigDecimal("10.00"), 4L, "NB0000000003")))
                .hasMessageContaining("your own accounts");
    }

    @Test
    void frozenDestinationIsRejected() {
        account("100245180073", 5L, "300.00", AccountStatus.ACTIVE);
        account("100245180099", 6L, "0.00", AccountStatus.FROZEN);

        assertThatThrownBy(() -> service.transfer(new TransferRequest("100245180073", "100245180099",
                new BigDecimal("10.00"), 5L, "NB0000000004")))
                .hasMessage("Destination account is FROZEN");
    }

    @Test
    void generatedAccountNumbersAreTwelveDigitsWithValidCheckDigit() {
        String n = new AccountNumberGenerator().next();
        assertThat(n).hasSize(12).startsWith("1002");
        assertThat(AccountNumberGenerator.luhnCheckDigit(n.substring(0, 11))).isEqualTo(n.charAt(11) - '0');
    }
    private LedgerEntry settled(String reference, Outcome outcome) {
        LedgerEntry entry = mock(LedgerEntry.class);
        when(entry.getReference()).thenReturn(reference);
        when(entry.getOutcome()).thenReturn(outcome);
        when(ledger.find(reference)).thenReturn(Optional.of(entry));
        return entry;
    }

    @Test
    void transferClaimsItsReferenceBeforeTouchingBalances() {
        account("100245180031", 3L, "50.00", AccountStatus.ACTIVE);
        account("100245180049", 3L, "0.00", AccountStatus.ACTIVE);

        service.transfer(new TransferRequest("100245180031", "100245180049", new BigDecimal("5.00"), 3L, "NB0000000010"));

        InOrder order = inOrder(ledger, repo);
        order.verify(ledger).record("NB0000000010", Outcome.APPLIED);
        order.verify(repo).lockByAccountNumber("100245180031");
    }

    @Test
    void retriedTransferWithAnAppliedReferenceDoesNotMoveMoneyAgain() {
        settled("NB0000000011", Outcome.APPLIED);
        Account from = account("100245180031", 3L, "45.00", AccountStatus.ACTIVE);
        Account to = account("100245180049", 3L, "5.00", AccountStatus.ACTIVE);
        when(repo.findByAccountNumber("100245180031")).thenReturn(Optional.of(from));
        when(repo.findByAccountNumber("100245180049")).thenReturn(Optional.of(to));

        var result = service.transfer(new TransferRequest("100245180031", "100245180049", new BigDecimal("5.00"), 3L, "NB0000000011"));

        assertThat(result.from().balance()).isEqualByComparingTo("45.00");
        assertThat(from.getBalance()).isEqualByComparingTo("45.00");
        verify(repo, never()).lockByAccountNumber(anyString());
        verify(ledger, never()).record(anyString(), any());
    }

    @Test
    void voidedReferenceCanNeverBeApplied() {
        settled("NB0000000012", Outcome.VOIDED);

        assertThatThrownBy(() -> service.transfer(new TransferRequest("100245180031", "100245180049",
                new BigDecimal("5.00"), 3L, "NB0000000012")))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.deposit(new DepositRequest("100245180049", new BigDecimal("5.00"), "NB0000000012")))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(repo, never()).lockByAccountNumber(anyString());
    }

    @Test
    void voidUnlessAppliedKeepsAnAppliedReference() {
        LedgerEntry applied = settled("NB0000000013", Outcome.APPLIED);

        assertThat(service.voidUnlessApplied("NB0000000013")).isSameAs(applied);
        verify(ledger, never()).record(anyString(), any());
    }

    @Test
    void voidUnlessAppliedVoidsAnUnknownReference() {
        service.voidUnlessApplied("NB0000000014");

        verify(ledger).record("NB0000000014", Outcome.VOIDED);
    }
}
