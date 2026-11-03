package com.novabank.account.audit;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/** Insert and query only. There is deliberately no update or delete. */
public interface AuditLogRepository extends Repository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    AuditLog save(AuditLog entry);

    Optional<AuditLog> findById(Long id);
}
