package com.novabank.account.account;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class AccountSpecs {

    private AccountSpecs() {
    }

    public static Specification<Account> search(AccountStatus status, AccountType type, Long customerId, String q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                p.add(cb.equal(root.get("accountType"), type));
            }
            if (customerId != null) {
                p.add(cb.equal(root.get("customerId"), customerId));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(root.<String>get("accountNumber"), like),
                        cb.like(cb.lower(root.<String>get("customerName")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
