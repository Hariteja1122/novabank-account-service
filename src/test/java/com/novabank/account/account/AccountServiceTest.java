package com.novabank.account.account;

import com.novabank.account.account.AccountDtos.TransferRequest;
import com.novabank.account.common.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final AccountRepository repo = mock(AccountRepository.class);
    private final AccountService service = new AccountService(repo, new AccountNumberGenerator());

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
}
