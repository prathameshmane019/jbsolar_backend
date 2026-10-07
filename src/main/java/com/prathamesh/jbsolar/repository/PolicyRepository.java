package com.prathamesh.jbsolar.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.prathamesh.jbsolar.domain.Policy;
import jakarta.persistence.LockModeType;

public interface PolicyRepository extends JpaRepository<Policy, UUID>, JpaSpecificationExecutor<Policy> {
    List<Policy> findAllByVendorId(UUID vendorId);
    long countByVendorId(UUID vendorId);
    long countByFarmerId(UUID farmerId);
    long countByPlanId(UUID planId);
    long countByCreatedById(UUID agentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Policy p where p.id = :id")
    java.util.Optional<Policy> findByIdForUpdate(@Param("id") UUID id);
}
