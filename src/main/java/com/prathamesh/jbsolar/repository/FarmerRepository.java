package com.prathamesh.jbsolar.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.prathamesh.jbsolar.domain.Farmer;

public interface FarmerRepository extends JpaRepository<Farmer, UUID>, JpaSpecificationExecutor<Farmer> {
    boolean existsByMobile(String mobile);
    List<Farmer> findAllByCreatedByVendorId(UUID vendorId);
    Optional<Farmer> findByIdAndCreatedByVendorId(UUID id, UUID vendorId);
    long countByCreatedById(UUID agentId);
}
