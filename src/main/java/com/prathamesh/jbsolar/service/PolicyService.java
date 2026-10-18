package com.prathamesh.jbsolar.service;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import com.prathamesh.jbsolar.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.PolicyRequest;
import com.prathamesh.jbsolar.api.dto.PolicyResponse;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.repository.FarmerRepository;
import com.prathamesh.jbsolar.repository.PolicyPlanRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.repository.VendorRepository;
import com.prathamesh.jbsolar.security.UserPrincipal;

@Service
@Transactional
public class PolicyService {
    private final PolicyRepository policies;
    private final FarmerRepository farmers;
    private final PolicyPlanRepository plans;
    private final VendorRepository vendors;
    private final VendorAgentRepository agents;
    private final MobileFileService mobileFiles;

    public PolicyService(PolicyRepository policies, FarmerRepository farmers, PolicyPlanRepository plans,
            VendorRepository vendors, VendorAgentRepository agents, MobileFileService mobileFiles) {
        this.policies = policies; this.farmers = farmers; this.plans = plans; this.vendors = vendors;
        this.agents = agents; this.mobileFiles = mobileFiles;
    }

    public PolicyResponse create(PolicyRequest request, UserPrincipal principal) {
        System.out.println("\n========== POLICY CREATE REQUEST ==========");
        System.out.println("Farmer ID       : " + request.farmerId());
        System.out.println("Policy Plan ID  : " + request.policyPlanId());
        System.out.println("Start Date      : " + request.startDate());
        System.out.println("Vendor ID       : " + request.vendorId());
        System.out.println("Agent ID        : " + request.agentId());
        System.out.println("Pump Power (HP) : " + request.pumpPowerHp());
        System.out.println("Motor Head (m)  : " + request.motorHeadMeters());
        System.out.println("Authenticated Role: " + principal.role());
        System.out.println("Authenticated Vendor ID: " + principal.vendorId());
        System.out.println("============================================\n");
        var farmer = (principal.role() == UserRole.ADMIN
                ? farmers.findById(request.farmerId())
                : farmers.findByIdAndCreatedByVendorId(request.farmerId(), principal.vendorId()))
                .filter(row -> row.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "farmer_not_found", "Farmer was not found in your accessible vendor data"));

        PolicyPlan plan = plans.findById(request.policyPlanId())
                .filter(p -> p.getDeletedAt() == null && p.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "active_policy_plan_not_found", "Active policy plan was not found"));

        UUID vendorId = principal.role() == UserRole.ADMIN ? request.vendorId() : principal.vendorId();

        if (vendorId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "vendor_required", "vendorId is required for admin-created policies");
        Vendor vendor = vendors.findById(vendorId)
                .filter(row -> row.getDeletedAt() == null && row.getStatus() == RecordStatus.ACTIVE).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "vendor_not_found", "Vendor was not found"));

        Policy policy = new Policy();
        policy.setPolicyNumber("JB-POL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        policy.setFarmer(farmer); policy.setPlan(plan); policy.setVendor(vendor);
        policy.setStartDate(request.startDate());
        policy.setEndDate(request.startDate().plusMonths(plan.getDurationMonths()));
        policy.setAmount(plan.getPrice());
        policy.setGstAmount(plan.getPrice().multiply(plan.getGstPercentage()).divide(new java.math.BigDecimal("100"), 2, RoundingMode.HALF_UP));
        policy.setTotalAmount(policy.getAmount().add(policy.getGstAmount()));
        applyPumpSetDetails(policy, request);
        if (principal.role() == UserRole.VENDOR_AGENT) {
            policy.setCreatedBy(agents.findById(principal.agentId()).orElseThrow(() ->
                    new ApiException(HttpStatus.FORBIDDEN, "agent_profile_missing", "Agent profile is unavailable")));
            if (policy.getCreatedBy().getDeletedAt() != null
                    || policy.getCreatedBy().getVendor().getDeletedAt() != null
                    || policy.getCreatedBy().getStatus() != RecordStatus.ACTIVE
                    || policy.getCreatedBy().getVendor().getStatus() != RecordStatus.ACTIVE) {
                throw new ApiException(HttpStatus.FORBIDDEN, "agent_profile_unavailable",
                        "Agent profile is unavailable");
            }
        } else {
            policy.setCreatedBy(resolveAgent(request.agentId(), vendor, null));
        }


        return toResponse(policies.save(policy));
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list(UserPrincipal principal) {
        return (principal.role() == UserRole.ADMIN ? policies.findAll() : policies.findAllByVendorId(principal.vendorId()))
                .stream().filter(row -> row.getDeletedAt() == null).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PolicyResponse> search(DataQuery query, UserPrincipal principal) {
        if (principal.role() != UserRole.ADMIN) {
            query = new DataQuery(query.page(), query.size(), query.sort(), query.direction(), query.search(),
                    query.status(), query.createdFrom(), query.createdTo(), principal.vendorId());
        }
        var page = policies.findAll(DataQuerySupport.specification(query,
                        List.of("policyNumber", "farmer.fullName", "farmer.customerCode",
                                "plan.name", "plan.planCode",
                                "vendor.name", "vendor.vendorCode",
                                "createdBy.fullName", "createdBy.agentCode"), true, "vendor.id", false),
                DataQuerySupport.pageable(query,
                        Set.of("policyNumber", "startDate", "endDate", "amount", "totalAmount",
                                "status", "createdAt", "updatedAt")));
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PolicyResponse get(UUID id, UserPrincipal principal) {
        return toResponse(requireActivePolicy(id, principal));
    }

    public PolicyResponse update(UUID id, PolicyRequest request, UserPrincipal principal) {
        Policy policy = requireActivePolicy(id, principal);
        if (policy.getStatus() != PolicyStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "policy_not_editable",
                    "Only pending policies can be updated");
        }

        var farmer = policy.getFarmer().getId().equals(request.farmerId())
                ? policy.getFarmer()
                : (principal.role() == UserRole.ADMIN
                ? farmers.findById(request.farmerId())
                : farmers.findByIdAndCreatedByVendorId(request.farmerId(), principal.vendorId()))
                .filter(row -> row.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "farmer_not_found",
                        "Farmer was not found in your accessible vendor data"));

        PolicyPlan plan = plans.findById(request.policyPlanId())
                .filter(row -> row.getDeletedAt() == null && row.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "active_policy_plan_not_found",
                        "Active policy plan was not found"));
        UUID vendorId = principal.role() == UserRole.ADMIN ? request.vendorId() : principal.vendorId();
        if (vendorId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "vendor_required",
                    "vendorId is required for admin-created policies");
        }
        Vendor vendor = vendors.findById(vendorId)
                .filter(row -> row.getDeletedAt() == null && row.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "vendor_not_found",
                        "Vendor was not found"));

        policy.setFarmer(farmer);
        policy.setPlan(plan);
        policy.setVendor(vendor);
        if (principal.role() == UserRole.ADMIN) {
            policy.setCreatedBy(resolveAgent(request.agentId(), vendor, policy.getCreatedBy()));
        }
        policy.setStartDate(request.startDate());
        policy.setEndDate(request.startDate().plusMonths(plan.getDurationMonths()));
        policy.setAmount(plan.getPrice());
        policy.setGstAmount(plan.getPrice().multiply(plan.getGstPercentage())
                .divide(new java.math.BigDecimal("100"), 2, RoundingMode.HALF_UP));
        policy.setTotalAmount(policy.getAmount().add(policy.getGstAmount()));
        applyPumpSetDetails(policy, request);
        return toResponse(policy);
    }

    public void delete(UUID id, UserPrincipal principal) {
        requireActivePolicy(id, principal).setDeletedAt(Instant.now());
    }

    public void restore(UUID id) {
        Policy policy = policies.findById(id).filter(row -> row.getDeletedAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "deleted_policy_not_found",
                        "Deleted policy was not found"));
        policy.setDeletedAt(null);
    }

    public void purge(UUID id) {
        Policy policy = policies.findById(id).filter(row -> row.getDeletedAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "deleted_policy_not_found",
                        "Deleted policy was not found"));
        mobileFiles.deletePolicyFiles(id);
        policies.delete(policy);
    }

    private Policy requireActivePolicy(UUID id, UserPrincipal principal) {
        return (principal.role() == UserRole.ADMIN ? policies.findById(id)
                : policies.findById(id).filter(row -> row.getVendor().getId().equals(principal.vendorId())))
                .filter(row -> row.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "policy_not_found",
                        "Policy was not found"));
    }
    private PolicyResponse toResponse(Policy p) {
        VendorAgent a = p.getCreatedBy();
        return new PolicyResponse(
                p.getId(), p.getPolicyNumber(),
                p.getFarmer().getId(), p.getFarmer().getFullName(),
                p.getPlan().getId(), p.getPlan().getName(),
                p.getVendor().getId(),
                p.getStartDate(), p.getEndDate(),
                p.getAmount(), p.getGstAmount(), p.getTotalAmount(), p.getStatus(),
                p.getPumpPowerHp(), p.getMotorHeadMeters(),
                a == null ? null : a.getId(), a == null ? null : a.getFullName(),
                p.getFarmer().getCustomerCode(), p.getPlan().getPlanCode(),
                p.getVendor().getName(), p.getVendor().getVendorCode(),
                a == null ? null : a.getAgentCode());
    }
    private void applyPumpSetDetails(Policy policy, PolicyRequest request) {
        policy.setPumpPowerHp(request.pumpPowerHp());
        policy.setMotorHeadMeters(request.motorHeadMeters());
    }

private VendorAgent resolveAgent(UUID agentId, Vendor vendor, VendorAgent current) {
    if (agentId == null) return null;
    if (current != null && current.getId().equals(agentId)
            && current.getVendor().getId().equals(vendor.getId())) {
        return current;
    }
    VendorAgent agent = agents.findById(agentId)
            .filter(a -> a.getDeletedAt() == null && a.getStatus() == RecordStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "agent_not_found",
                    "Active agent was not found"));
    if (!agent.getVendor().getId().equals(vendor.getId())) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "agent_vendor_mismatch",
                "Agent does not belong to the selected vendor");
    }
    return agent;
}
}
