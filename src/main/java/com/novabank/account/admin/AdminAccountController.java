package com.novabank.account.admin;

import com.novabank.account.account.AccountDtos.AccountDto;
import com.novabank.account.account.AccountDtos.AccountSummary;
import com.novabank.account.account.AccountDtos.OpenAccountRequest;
import com.novabank.account.account.AccountDtos.StatusRequest;
import com.novabank.account.account.AccountRepository;
import com.novabank.account.account.AccountService;
import com.novabank.account.account.AccountSpecs;
import com.novabank.account.account.AccountStatus;
import com.novabank.account.account.AccountType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AccountService service;
    private final AccountRepository accounts;

    public AdminAccountController(AccountService service, AccountRepository accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping
    public Page<AccountDto> search(@RequestParam(required = false) AccountStatus status,
                                   @RequestParam(required = false) AccountType type,
                                   @RequestParam(required = false) Long customerId,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "openedAt"));
        return accounts.findAll(AccountSpecs.search(status, type, customerId, q), pageable).map(AccountDto::from);
    }

    @GetMapping("/summary")
    public AccountSummary summary() {
        return service.summary();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto open(@Valid @RequestBody OpenAccountRequest req) {
        return service.open(req);
    }

    @PatchMapping("/{accountNumber}/status")
    public AccountDto changeStatus(@PathVariable String accountNumber, @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(accountNumber, req.status());
    }
}
