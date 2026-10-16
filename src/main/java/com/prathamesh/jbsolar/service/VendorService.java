package com.prathamesh.jbsolar.service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.Set;

import com.prathamesh.jbsolar.domain.VendorAgent;
import com.prathamesh.jbsolar.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.VendorRequest;
import com.prathamesh.jbsolar.api.dto.VendorResponse;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.domain.Vendor;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.repository.VendorRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.security.UserPrincipal;

@Service
@Transactional
public class VendorService {
    private final VendorRepository vendors;
    private final VendorAgentRepository agents;
    private final PolicyRepository policies;
    public VendorService(VendorRepository vendors, VendorAgentRepository agents, PolicyRepository policies) {
        this.vendors = vendors;
        this.agents = agents;
        this.policies = policies;
    }

    public VendorResponse create(VendorRequest request) {
        Vendor vendor = new Vendor();
        update(vendor, request);
        return toResponse(vendors.save(vendor));
    }

    public VendorResponse update(UUID id, VendorRequest request) {
        Vendor vendor = requireActiveVendor(id);
        update(vendor, request);
        return toResponse(vendor);
    }

    public VendorResponse setStatus(UUID id, RecordStatus status) {
        Vendor vendor = requireActiveVendor(id);
        vendor.setStatus(status);
        return toResponse(vendor);
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> list() {
        return vendors.findAll().stream().filter(row -> row.getDeletedAt() == null).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<VendorResponse> search(DataQuery query) {
        validateDateRange(query);
        var page = vendors.findAll(DataQuerySupport.specification(query,
                        List.of("name", "contactPerson", "mobile", "email"), true, null, false),
                DataQuerySupport.pageable(query,
                        Set.of("name", "mobile", "email", "status", "createdAt", "updatedAt")));
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public VendorResponse get(UUID id, UserPrincipal principal) {
        if (principal.role() == UserRole.VENDOR_AGENT
                && !id.equals(principal.vendorId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "vendor_not_found", "Vendor was not found");
        }
        return toResponse(requireActiveVendor(id));
    }

    private Vendor requireActiveVendor(UUID id) {
        return vendors.findById(id).filter(row -> row.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "vendor_not_found", "Vendor was not found"));
    }

    private void update(Vendor vendor, VendorRequest request) {
        vendor.setName(request.name().trim());
        vendor.setContactPerson(request.contactPerson());
        vendor.setMobile(request.mobile());
        vendor.setEmail(request.email());
    }

    private void validateDateRange(DataQuery query) {
        if (query.createdFrom() != null && query.createdTo() != null
                && query.createdFrom().isAfter(query.createdTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_date_range",
                    "createdFrom must be on or before createdTo");
        }
    }

    public void delete(UUID id) { requireActiveVendor(id).setDeletedAt(Instant.now()); }

    public void restore(UUID id) {
        Vendor vendor = vendors.findById(id).filter(row -> row.getDeletedAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "deleted_vendor_not_found",
                        "Deleted vendor was not found"));
        vendor.setDeletedAt(null);
    }

    public void purge(UUID id) {
        Vendor vendor = vendors.findById(id).filter(row -> row.getDeletedAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "deleted_vendor_not_found",
                        "Deleted vendor was not found"));
        if (agents.countByVendorId(id) > 0 || policies.countByVendorId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "record_has_dependencies",
                    "Vendor is referenced by agents or policy history and cannot be permanently deleted");
        }
        vendors.delete(vendor);
    }


    private VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(vendor.getId(), vendor.getName(), vendor.getContactPerson(), vendor.getMobile(),
                vendor.getEmail(), vendor.getStatus(), vendor.getCreatedAt());
    }
}
