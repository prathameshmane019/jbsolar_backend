package com.prathamesh.jbsolar.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.prathamesh.jbsolar.domain.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
    Optional<Invoice> findByPolicyId(UUID policyId);
    Optional<Invoice> findByIdAndPolicyVendorId(UUID id, UUID vendorId);

    // Loads everything the invoice screen needs in one query instead of one query per row.
    @Override
    @EntityGraph(attributePaths = {"policy", "policy.farmer", "policy.plan", "policy.vendor", "payment"})
    Page<Invoice> findAll(Specification<Invoice> spec, Pageable pageable);
}