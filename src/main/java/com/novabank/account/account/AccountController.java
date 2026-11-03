package com.novabank.account.account;

import com.novabank.account.audit.Audited;
import com.novabank.account.account.AccountDtos.AccountDto;
import com.novabank.account.account.AccountDtos.OpenAccountRequest;
import com.novabank.account.account.AccountDtos.OpenOwnAccountRequest;
import com.novabank.account.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** Customer facing account endpoints. Callers only ever see their own accounts. */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<AccountDto> myAccounts(@AuthenticationPrincipal Jwt jwt) {
        return service.forCustomer(CurrentUser.id(jwt));
    }

    @GetMapping("/{accountNumber}")
    public AccountDto get(@PathVariable String accountNumber, @AuthenticationPrincipal Jwt jwt) {
        return service.get(accountNumber, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt));
    }

    @PostMapping
    @Audited(action = "ACCOUNT_OPEN", targetType = "ACCOUNT")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDto openOwn(@Valid @RequestBody OpenOwnAccountRequest req, @AuthenticationPrincipal Jwt jwt) {
        return service.open(new OpenAccountRequest(CurrentUser.id(jwt), jwt.getClaimAsString("name"),
                req.accountType(), BigDecimal.ZERO));
    }
}
