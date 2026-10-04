package com.prathamesh.jbsolar.service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.PolicyPlanRequest;
import com.prathamesh.jbsolar.api.dto.PolicyPlanResponse;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.domain.PolicyPlan;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.repository.PolicyPlanRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;

@Service
@Transactional
public class PolicyPlanService {
    private final PolicyPlanRepository plans;
    private final PolicyRepository policies;
    public PolicyPlanService(PolicyPlanRepository plans, PolicyRepository policies) {
        this.plans = plans;
        this.policies = policies;
    }

    public PolicyPlanResponse create(PolicyPlanRequest request) {
        PolicyPlan plan = new PolicyPlan();
        apply(plan, request);
        plan.setStatus(RecordStatus.ACTIVE);
        return toResponse(plans.save(plan));
    }

    public PolicyPlanResponse update(UUID id, PolicyPlanRequest request) {
        PolicyPlan plan = requireActivePlan(id);
        apply(plan, request);
        return toResponse(plan);
    }

    public PolicyPlanResponse setStatus(UUID id, RecordStatus status) {
        PolicyPlan plan = requireActivePlan(id);
        plan.setStatus(status);
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<PolicyPlanResponse> list(boolean activeOnly) {
        return (activeOnly ? plans.findAllByStatus(RecordStatus.ACTIVE) : plans.findAll()).stream()
                .filter(row -> row.getDeletedAt() == null).map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PolicyPlanResponse> search(DataQuery query, boolean activeOnly) {
        if (activeOnly && (query.status() == null || query.status().isBlank())) {
            query = new DataQuery(query.page(), query.size(), query.sort(), query.direction(), query.search(),
                    RecordStatus.ACTIVE.name(), query.createdFrom(), query.createdTo(), query.vendorId());
        }
        var page = plans.findAll(DataQuerySupport.specification(query,
                        List.of("name", "description", "termsAndConditions"), true, null, false),
                DataQuerySupport.pageable(query,
                        Set.of("name", "price", "durationMonths", "gstPercentage", "status", "createdAt", "updatedAt")));
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PolicyPlanResponse get(UUID id) { return toResponse(requireActivePlan(id)); }

    public void delete(UUID id) { requireActivePlan(id).setDeletedAt(Instant.now()); }

    public void restore(UUID id) {
        PolicyPlan plan = plans.findById(id).filter(row -> row.getDeletedAt() != null).orElseThrow(this::notFound);
        plan.setDeletedAt(null);
    }

    public void purge(UUID id) {
        PolicyPlan plan = plans.findById(id).filter(row -> row.getDeletedAt() != null).orElseThrow(this::notFound);
        if (policies.countByPlanId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "record_has_dependencies",
                    "Policy plan is referenced by policy history and cannot be permanently deleted");
        }
        plans.delete(plan);
    }

    private PolicyPlan requireActivePlan(UUID id) {
        return plans.findById(id).filter(row -> row.getDeletedAt() == null).orElseThrow(this::notFound);
    }

    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "policy_plan_not_found", "Policy plan was not found"); }
    private void apply(PolicyPlan plan, PolicyPlanRequest request) {
        plan.setName(request.name().trim()); plan.setDescription(request.description());
        plan.setDurationMonths(request.durationMonths()); plan.setPrice(request.price());
        plan.setGstPercentage(request.gstPercentage()); plan.setTermsAndConditions(request.termsAndConditions());
    }
    private PolicyPlanResponse toResponse(PolicyPlan p) {
        return new PolicyPlanResponse(p.getId(), p.getName(), p.getDescription(), p.getDurationMonths(), p.getPrice(),
                p.getGstPercentage(), p.getTermsAndConditions(), p.getStatus());
    }
}
