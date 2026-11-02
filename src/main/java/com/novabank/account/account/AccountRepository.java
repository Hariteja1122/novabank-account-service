package com.novabank.account.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerIdOrderByOpenedAtAsc(Long customerId);

    long countByCustomerIdAndStatusNot(Long customerId, AccountStatus status);

    boolean existsByAccountNumber(String accountNumber);

    long countByStatus(AccountStatus status);

    /** Row lock (SELECT ... FOR UPDATE) held until the surrounding transaction commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :number")
    Optional<Account> lockByAccountNumber(@Param("number") String number);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.status <> com.novabank.account.account.AccountStatus.CLOSED")
    BigDecimal totalDeposits();

    @Query("select a.accountType, count(a), coalesce(sum(a.balance), 0) from Account a group by a.accountType")
    List<Object[]> totalsByType();
}
