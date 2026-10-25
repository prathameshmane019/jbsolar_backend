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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @Query("select farmer from Farmer farmer join farmer.createdBy agent "
            + "where farmer.id = :farmerId and agent.vendor.id = :vendorId")
    Optional<Farmer> findByIdAndCreatedByVendorId(@Param("farmerId") UUID farmerId,
            @Param("vendorId") UUID vendorId);
    long countByCreatedById(UUID agentId);
}
