# novabank-account-service

Customer accounts and balances. The only service allowed to change a balance.

| | |
|---|---|
| Port | 8082 (Cloud Run: 8080 via `PORT`) |
| Db2 schema | `NB_ACCT` (table `ACCOUNTS`) |
| Called by | auth-service (open default account), transaction-service (transfer, deposit, ownership) |
| Swagger (local/dev) | http://localhost:8082/swagger-ui.html |

## How a transfer stays correct

* Both rows are locked with `SELECT ... FOR UPDATE` in account number order, so two
  opposite transfers between the same accounts cannot deadlock.
* `@Version` optimistic locking on the `VERSION` column catches any update that slips
  past the row lock; the API answers 409 and the caller retries.
* A Db2 `CHECK (BALANCE >= 0)` constraint is the last line of defence against overdrafts.

## Endpoints

| Method | Path | Who | Purpose |
|---|---|---|---|
| GET | `/api/accounts` | customer | My accounts |
| GET | `/api/accounts/{accountNumber}` | owner or ADMIN | Account detail |
| POST | `/api/accounts` | customer | Open another account for myself `{accountType}` (max 4) |
| GET | `/api/admin/accounts?status=&type=&customerId=&q=` | ADMIN | Search all accounts |
| GET | `/api/admin/accounts/summary` | ADMIN | Totals for the dashboard |
| POST | `/api/admin/accounts` | ADMIN | Open an account for a customer, optional initial deposit |
| PATCH | `/api/admin/accounts/{accountNumber}/status` | ADMIN | Freeze, unfreeze or close |
| GET | `/internal/accounts?customerId=` | internal key | Accounts of a customer |
| POST | `/internal/accounts` | internal key | Open account |
| POST | `/internal/accounts/transfer` | internal key | Atomic debit + credit |
| POST | `/internal/accounts/deposit` | internal key | Credit |

`/internal/**` requires the `X-Internal-Api-Key` header and is never routed by the gateway.

## Run

```bash
../novabank-platform/dev.sh db     # Db2 only, then run "AccountService (local)" in IntelliJ
../novabank-platform/dev.sh up     # or the whole stack
```

Configuration variables are the same as auth-service minus `ACCOUNT_SERVICE_URL`.
