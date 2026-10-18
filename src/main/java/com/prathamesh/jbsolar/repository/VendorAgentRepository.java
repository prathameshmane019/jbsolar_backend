package com.prathamesh.jbsolar.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import com.prathamesh.jbsolar.domain.VendorAgent;

public interface VendorAgentRepository extends JpaRepository<VendorAgent, UUID>, JpaSpecificationExecutor<VendorAgent> {
    @Override
    @EntityGraph(attributePaths = {"vendor", "user"})
    Optional<VendorAgent> findById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"vendor", "user"})
    Page<VendorAgent> findAll(Specification<VendorAgent> specification, Pageable pageable);

    Optional<VendorAgent> findByUserId(UUID userId);
    @EntityGraph(attributePaths = "vendor")
    Optional<VendorAgent> findWithVendorByUserId(UUID userId);
    List<VendorAgent> findAllByDeletedAtIsNotNull();
    long countByVendorId(UUID vendorId);
}
