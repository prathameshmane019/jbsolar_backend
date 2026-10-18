package com.prathamesh.jbsolar.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import com.prathamesh.jbsolar.domain.Farmer;

public interface FarmerRepository extends JpaRepository<Farmer, UUID>, JpaSpecificationExecutor<Farmer> {
    @Override
    @EntityGraph(attributePaths = "createdBy")
    Page<Farmer> findAll(Specification<Farmer> specification, Pageable pageable);

    boolean existsByMobile(String mobile);
    boolean existsByAadhaarHash(String aadhaarHash);
    boolean existsByAadhaarHashAndIdNot(String aadhaarHash, UUID id);
    List<Farmer> findAllByCreatedByVendorId(UUID vendorId);
    List<Farmer> findAllByDeletedAtIsNotNull();
    Optional<Farmer> findByIdAndCreatedByVendorId(UUID id, UUID vendorId);
    long countByCreatedById(UUID agentId);
}
