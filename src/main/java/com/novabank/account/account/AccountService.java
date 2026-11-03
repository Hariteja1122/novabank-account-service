package com.novabank.account.account;

import com.novabank.account.account.AccountDtos.AccountDto;
import com.novabank.account.account.AccountDtos.AccountSummary;
import com.novabank.account.account.AccountDtos.BalanceChange;
import com.novabank.account.account.AccountDtos.DepositRequest;
import com.novabank.account.account.AccountDtos.OpenAccountRequest;
import com.novabank.account.account.AccountDtos.TransferRequest;
import com.novabank.account.account.AccountDtos.TransferResult;
import com.novabank.account.account.AccountDtos.TypeTotal;
import com.novabank.account.common.ApiException;
import com.novabank.account.ledger.LedgerEntry;
import com.novabank.account.ledger.LedgerEntry.Outcome;
import com.novabank.account.ledger.TransferLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    static final int MAX_OPEN_ACCOUNTS_PER_CUSTOMER = 4;

    private final AccountRepository accounts;
    private final AccountNumberGenerator numbers;
    private final TransferLedger ledger;

    public AccountService(AccountRepository accounts, AccountNumberGenerator numbers, TransferLedger ledger) {
        this.accounts = accounts;
        this.numbers = numbers;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<AccountDto> forCustomer(Long customerId) {
        return accounts.findByCustomerIdOrderByOpenedAtAsc(customerId).stream().map(AccountDto::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountDto get(String accountNumber, Long callerId, boolean admin) {
        Account account = find(accountNumber);
        if (!admin && !account.getCustomerId().equals(callerId)) {
            // Same message as "not found" so customers cannot probe other people's accounts.
            throw ApiException.notFound("Account not found");
        }
        return AccountDto.from(account);
    }

    @Transactional
    public AccountDto open(OpenAccountRequest req) {
        if (accounts.countByCustomerIdAndStatusNot(req.customerId(), AccountStatus.CLOSED) >= MAX_OPEN_ACCOUNTS_PER_CUSTOMER) {
            throw ApiException.badRequest("A customer can hold at most " + MAX_OPEN_ACCOUNTS_PER_CUSTOMER + " open accounts");
        }
        Account account = new Account();
        account.setAccountNumber(uniqueNumber());
        account.setCustomerId(req.customerId());
        account.setCustomerName(req.customerName());
        account.setAccountType(req.accountType());
        account.setCurrency("USD");
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(req.initialDeposit() == null ? BigDecimal.ZERO : req.initialDeposit());
        account = accounts.save(account);
        log.info("Opened {} account {} for customer {}", req.accountType(), account.getAccountNumber(), req.customerId());
        return AccountDto.from(account);
    }

    @Transactional
    public AccountDto changeStatus(String accountNumber, AccountStatus status) {
        Account account = find(accountNumber);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw ApiException.badRequest("Closed accounts cannot be changed");
        }
        if (status == AccountStatus.CLOSED && account.getBalance().signum() != 0) {
            throw ApiException.badRequest("Only accounts with a zero balance can be closed");
        }
        account.setStatus(status);
        log.info("Account {} set to {}", accountNumber, status);
        return AccountDto.from(account);
    }

    /**
     * Moves money between two accounts in one local Db2 transaction. Both rows are
     * locked in account number order, so two opposite transfers can never deadlock.
     * The reference is recorded as APPLIED in the same transaction, so a retry never
     * moves money twice and reconciliation can tell whether the money moved.
     */
    @Transactional
    public TransferResult transfer(TransferRequest req) {
        if (req.fromAccount().equals(req.toAccount())) {
            throw ApiException.badRequest("Source and destination accounts must be different");
        }
        Optional<LedgerEntry> settled = ledger.find(req.reference());
        if (settled.isPresent()) {
            requireApplied(settled.get());
            return new TransferResult(req.reference(),
                    new BalanceChange(req.fromAccount(), find(req.fromAccount()).getBalance()),
                    new BalanceChange(req.toAccount(), find(req.toAccount()).getBalance()));
        }
        // Claim the reference first. If a check below fails, the claim rolls back with it.
        ledger.record(req.reference(), Outcome.APPLIED);
        boolean fromFirst = req.fromAccount().compareTo(req.toAccount()) < 0;
        Account first = lock(fromFirst ? req.fromAccount() : req.toAccount());
        Account second = lock(fromFirst ? req.toAccount() : req.fromAccount());
        Account from = fromFirst ? first : second;
        Account to = fromFirst ? second : first;

        if (req.requestedBy() != null && !from.getCustomerId().equals(req.requestedBy())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only transfer from your own accounts");
        }
        if (!from.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Source account is " + from.getStatus());
        }
        if (!to.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Destination account is " + to.getStatus());
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Cross currency transfers are not supported");
        }
        if (from.getBalance().compareTo(req.amount()) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
        }

        from.debit(req.amount());
        to.credit(req.amount());
        log.info("Transfer {} moved {} from {} to {}", req.reference(), req.amount(), from.getAccountNumber(), to.getAccountNumber());
        return new TransferResult(req.reference(),
                new BalanceChange(from.getAccountNumber(), from.getBalance()),
                new BalanceChange(to.getAccountNumber(), to.getBalance()));
    }

    @Transactional
    public BalanceChange deposit(DepositRequest req) {
        Optional<LedgerEntry> settled = ledger.find(req.reference());
        if (settled.isPresent()) {
            requireApplied(settled.get());
            return new BalanceChange(req.toAccount(), find(req.toAccount()).getBalance());
        }
        ledger.record(req.reference(), Outcome.APPLIED);
        Account to = lock(req.toAccount());
        if (!to.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Destination account is " + to.getStatus());
        }
        to.credit(req.amount());
        log.info("Deposit {} credited {} to {}", req.reference(), req.amount(), to.getAccountNumber());
        return new BalanceChange(to.getAccountNumber(), to.getBalance());
    }

    /**
     * Reconciliation: the final outcome for a reference. If the money never moved, the
     * reference is voided so a late or retried request can no longer apply it.
     */
    @Transactional
    public LedgerEntry voidUnlessApplied(String reference) {
        return ledger.find(reference).orElseGet(() -> {
            log.warn("Voiding reference {}: no balance change was ever applied for it", reference);
            return ledger.record(reference, Outcome.VOIDED);
        });
    }

    @Transactional(readOnly = true)
    public AccountSummary summary() {
        List<TypeTotal> byType = accounts.totalsByType().stream()
                .map(r -> new TypeTotal((AccountType) r[0], ((Number) r[1]).longValue(), new BigDecimal(r[2].toString())))
                .toList();
        return new AccountSummary(accounts.count(),
                accounts.countByStatus(AccountStatus.ACTIVE),
                accounts.countByStatus(AccountStatus.FROZEN),
                accounts.totalDeposits(),
                byType);
    }

    private static void requireApplied(LedgerEntry entry) {
        if (entry.getOutcome() == Outcome.VOIDED) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Reference " + entry.getReference() + " was cancelled because it did not complete in time");
        }
    }

    private Account find(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber)
                .orElseThrow(() -> ApiException.notFound("Account not found"));
    }

    private Account lock(String accountNumber) {
        return accounts.lockByAccountNumber(accountNumber)
                .orElseThrow(() -> ApiException.notFound("Account " + accountNumber + " not found"));
    }

    private String uniqueNumber() {
        for (int i = 0; i < 5; i++) {
            String candidate = numbers.next();
            if (!accounts.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique account number");
    }
}
