package com.prathamesh.jbsolar.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.FarmerRequest;
import com.prathamesh.jbsolar.api.dto.FarmerResponse;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.domain.Farmer;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.repository.FarmerRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.security.UserPrincipal;

@Service
@Transactional
public class FarmerService {
    private final FarmerRepository farmers;
    private final VendorAgentRepository agents;
    private final PolicyRepository policies;
    public FarmerService(FarmerRepository farmers, VendorAgentRepository agents, PolicyRepository policies) {
        this.farmers = farmers;
        this.agents = agents;
        this.policies = policies;
    }

    public FarmerResponse create(FarmerRequest request, UserPrincipal principal) {
        Farmer farmer = new Farmer();
        if (principal.role() == UserRole.VENDOR_AGENT) {
            farmer.setCreatedBy(agents.findById(principal.agentId()).orElseThrow(() ->
                    new ApiException(HttpStatus.FORBIDDEN, "agent_profile_missing", "Agent profile is unavailable")));
            if (farmer.getCreatedBy().getDeletedAt() != null
                    || farmer.getCreatedBy().getVendor().getDeletedAt() != null
                    || farmer.getCreatedBy().getStatus() != RecordStatus.ACTIVE
                    || farmer.getCreatedBy().getVendor().getStatus() != RecordStatus.ACTIVE) {
                throw new ApiException(HttpStatus.FORBIDDEN, "agent_profile_unavailable",
                        "Agent profile is unavailable");
            }
        }
        farmer.setCustomerCode("F-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        apply(farmer, request);
        return toResponse(farmers.save(farmer));
    }

    @Transactional(readOnly = true)
    public List<FarmerResponse> list(UserPrincipal principal) {
        List<Farmer> result = principal.role() == UserRole.ADMIN
                ? farmers.findAll() : farmers.findAllByCreatedByVendorId(principal.vendorId());
        return result.stream().filter(row -> row.getDeletedAt() == null).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<FarmerResponse> search(DataQuery query, UserPrincipal principal) {
        if (principal.role() != UserRole.ADMIN) {
            query = withVendorId(query, principal.vendorId());
        }
        var page = farmers.findAll(DataQuerySupport.specification(query,
                        List.of("customerCode", "fullName", "mobile", "address", "district", "taluka", "village"),
                        false, "createdBy.vendor.id", false),
                DataQuerySupport.pageable(query, Set.of("customerCode", "fullName", "mobile",
                        "district", "taluka", "village", "createdAt", "updatedAt")));
        return PageResponse.from(page.map(this::toResponse));
    }

    private DataQuery withVendorId(DataQuery query, UUID vendorId) {
        return new DataQuery(query.page(), query.size(), query.sort(), query.direction(), query.search(),
                query.status(), query.createdFrom(), query.createdTo(), vendorId);
    }

    @Transactional(readOnly = true)
    public FarmerResponse get(UUID id, UserPrincipal principal) {
        Farmer farmer = principal.role() == UserRole.ADMIN
                ? farmers.findById(id).filter(row -> row.getDeletedAt() == null).orElseThrow(() -> notFound())
                : farmers.findByIdAndCreatedByVendorId(id, principal.vendorId())
                        .filter(row -> row.getDeletedAt() == null).orElseThrow(() -> notFound());
        return toResponse(farmer);
    }

    public FarmerResponse update(UUID id, FarmerRequest request, UserPrincipal principal) {
        Farmer farmer = requireActiveFarmer(id, principal);
        apply(farmer, request);
        return toResponse(farmer);
    }

    public void delete(UUID id, UserPrincipal principal) {
        Farmer farmer = requireActiveFarmer(id, principal);
        farmer.setDeletedAt(Instant.now());
    }

    public void purge(UUID id) {
        Farmer farmer = farmers.findById(id).filter(row -> row.getDeletedAt() != null)
                .orElseThrow(this::notFound);
        if (policies.countByFarmerId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "record_has_dependencies",
                    "Farmer is referenced by policy history and cannot be permanently deleted");
        }
        farmers.delete(farmer);
    }

    public void restore(UUID id) {
        Farmer farmer = farmers.findById(id).filter(row -> row.getDeletedAt() != null)
                .orElseThrow(this::notFound);
        farmer.setDeletedAt(null);
    }

    private Farmer requireActiveFarmer(UUID id, UserPrincipal principal) {
        return (principal.role() == UserRole.ADMIN
                ? farmers.findById(id)
                : farmers.findByIdAndCreatedByVendorId(id, principal.vendorId()))
                .filter(row -> row.getDeletedAt() == null)
                .orElseThrow(this::notFound);
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "farmer_not_found", "Farmer was not found"); }
    private void apply(Farmer farmer, FarmerRequest request) {
        farmer.setFullName(request.fullName().trim()); farmer.setMobile(request.mobile().trim());
        farmer.setAddress(request.address()); farmer.setDistrict(request.district());
        farmer.setTaluka(request.taluka()); farmer.setVillage(request.village());
    }
    private FarmerResponse toResponse(Farmer farmer) {
        return new FarmerResponse(farmer.getId(), farmer.getCustomerCode(), farmer.getFullName(), farmer.getMobile(),
                farmer.getAddress(), farmer.getDistrict(), farmer.getTaluka(), farmer.getVillage(),
                farmer.getCreatedBy() == null ? null : farmer.getCreatedBy().getId(), farmer.getCreatedAt());
    }
}
