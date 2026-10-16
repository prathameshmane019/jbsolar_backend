package com.prathamesh.jbsolar.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import com.prathamesh.jbsolar.domain.VendorAgent;

public interface VendorAgentRepository extends JpaRepository<VendorAgent, UUID>, JpaSpecificationExecutor<VendorAgent> {
    Optional<VendorAgent> findByUserId(UUID userId);
    @EntityGraph(attributePaths = "vendor")
    Optional<VendorAgent> findWithVendorByUserId(UUID userId);
    long countByVendorId(UUID vendorId);
}
