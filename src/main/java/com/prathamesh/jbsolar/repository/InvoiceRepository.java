package com.prathamesh.jbsolar.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prathamesh.jbsolar.domain.Invoice;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByPolicyId(UUID policyId);
    Optional<Invoice> findByIdAndPolicyVendorId(UUID id, UUID vendorId);
}
