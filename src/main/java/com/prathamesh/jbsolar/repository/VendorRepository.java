package com.prathamesh.jbsolar.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.prathamesh.jbsolar.domain.Vendor;

public interface VendorRepository extends JpaRepository<Vendor, UUID>, JpaSpecificationExecutor<Vendor> {
}
