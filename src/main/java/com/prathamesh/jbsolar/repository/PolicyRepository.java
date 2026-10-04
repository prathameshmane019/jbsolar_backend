package com.prathamesh.jbsolar.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.prathamesh.jbsolar.domain.Policy;

public interface PolicyRepository extends JpaRepository<Policy, UUID>, JpaSpecificationExecutor<Policy> {
    List<Policy> findAllByVendorId(UUID vendorId);
    long countByVendorId(UUID vendorId);
    long countByFarmerId(UUID farmerId);
    long countByPlanId(UUID planId);
    long countByCreatedById(UUID agentId);
}
