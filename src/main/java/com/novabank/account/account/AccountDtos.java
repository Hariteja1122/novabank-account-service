package com.novabank.account.account;

import com.novabank.account.ledger.LedgerEntry;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountDto(Long id, String accountNumber, Long customerId, String customerName,
                             AccountType accountType, BigDecimal balance, String currency,
                             AccountStatus status, Instant openedAt) {

        public static AccountDto from(Account a) {
            return new AccountDto(a.getId(), a.getAccountNumber(), a.getCustomerId(), a.getCustomerName(),
                    a.getAccountType(), a.getBalance(), a.getCurrency(), a.getStatus(), a.getOpenedAt());
        }
    }

    /** Customer self-service: open another account for myself. */
    public record OpenOwnAccountRequest(@NotNull AccountType accountType) {
    }

    /** Admin or internal: open an account for a given customer. */
    public record OpenAccountRequest(
            @NotNull Long customerId,
            @NotBlank String customerName,
            @NotNull AccountType accountType,
            @DecimalMin(value = "0.00") @Digits(integer = 13, fraction = 2) BigDecimal initialDeposit) {
    }

    public record StatusRequest(@NotNull AccountStatus status) {
    }

    public record TransferRequest(
            @NotBlank @Pattern(regexp = "\\d{12}") String fromAccount,
            @NotBlank @Pattern(regexp = "\\d{12}") String toAccount,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            Long requestedBy,
            @NotBlank String reference) {
    }

    public record DepositRequest(
            @NotBlank @Pattern(regexp = "\\d{12}") String toAccount,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotBlank String reference) {
    }

    public record BalanceChange(String accountNumber, BigDecimal balance) {
    }

    public record TransferResult(String reference, BalanceChange from, BalanceChange to) {
    }

    public record LedgerEntryDto(String reference, LedgerEntry.Outcome outcome, Instant recordedAt) {

        public static LedgerEntryDto from(LedgerEntry e) {
            return new LedgerEntryDto(e.getReference(), e.getOutcome(), e.getRecordedAt());
        }
    }

    public record TypeTotal(AccountType accountType, long count, BigDecimal balance) {
    }

    public record AccountSummary(long totalAccounts, long activeAccounts, long frozenAccounts,
                                 BigDecimal totalDeposits, java.util.List<TypeTotal> byType) {
    }
}
