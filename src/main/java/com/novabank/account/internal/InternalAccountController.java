package com.novabank.account.internal;

import com.novabank.account.account.AccountDtos.AccountDto;
import com.novabank.account.account.AccountDtos.BalanceChange;
import com.novabank.account.account.AccountDtos.DepositRequest;
import com.novabank.account.account.AccountDtos.OpenAccountRequest;
import com.novabank.account.account.AccountDtos.TransferRequest;
import com.novabank.account.account.AccountDtos.TransferResult;
import com.novabank.account.account.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service to service API used by auth-service and transaction-service. Not routed by
 * the gateway and protected by the internal API key (see SecurityConfig).
 */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

    private final AccountService service;

    public InternalAccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountDto> byCustomer(@RequestParam Long customerId) {
        return service.forCustomer(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto open(@Valid @RequestBody OpenAccountRequest req) {
        return service.open(req);
    }

    @PostMapping("/transfer")
    public TransferResult transfer(@Valid @RequestBody TransferRequest req) {
        return service.transfer(req);
    }

    @PostMapping("/deposit")
    public BalanceChange deposit(@Valid @RequestBody DepositRequest req) {
        return service.deposit(req);
    }
}
