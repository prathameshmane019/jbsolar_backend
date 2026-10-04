package com.prathamesh.jbsolar.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.RecycleBinEntry;
import com.prathamesh.jbsolar.repository.FarmerRepository;
import com.prathamesh.jbsolar.repository.PolicyPlanRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.repository.VendorRepository;

@Service
@Transactional
public class RecycleBinService {
    private final VendorRepository vendors;
    private final VendorAgentRepository agents;
    private final FarmerRepository farmers;
    private final PolicyPlanRepository plans;
    private final PolicyRepository policies;
    private final VendorService vendorService;
    private final AgentService agentService;
    private final FarmerService farmerService;
    private final PolicyPlanService planService;
    private final PolicyService policyService;

    public RecycleBinService(VendorRepository vendors, VendorAgentRepository agents, FarmerRepository farmers,
            PolicyPlanRepository plans, PolicyRepository policies, VendorService vendorService,
            AgentService agentService, FarmerService farmerService, PolicyPlanService planService,
            PolicyService policyService) {
        this.vendors = vendors;
        this.agents = agents;
        this.farmers = farmers;
        this.plans = plans;
        this.policies = policies;
        this.vendorService = vendorService;
        this.agentService = agentService;
        this.farmerService = farmerService;
        this.planService = planService;
        this.policyService = policyService;
    }

    @Transactional(readOnly = true)
    public List<RecycleBinEntry> list() {
        return Stream.of(
                vendors.findAll().stream().filter(row -> row.getDeletedAt() != null)
                        .map(row -> new RecycleBinEntry("vendors", row.getId(), row.getName(), row.getDeletedAt())),
                agents.findAll().stream().filter(row -> row.getDeletedAt() != null)
                        .map(row -> new RecycleBinEntry("agents", row.getId(), row.getFullName(), row.getDeletedAt())),
                farmers.findAll().stream().filter(row -> row.getDeletedAt() != null)
                        .map(row -> new RecycleBinEntry("farmers", row.getId(),
                                row.getCustomerCode() + " - " + row.getFullName(), row.getDeletedAt())),
                plans.findAll().stream().filter(row -> row.getDeletedAt() != null)
                        .map(row -> new RecycleBinEntry("policy-plans", row.getId(), row.getName(), row.getDeletedAt())),
                policies.findAll().stream().filter(row -> row.getDeletedAt() != null)
                        .map(row -> new RecycleBinEntry("policies", row.getId(), row.getPolicyNumber(), row.getDeletedAt())))
                .flatMap(stream -> stream)
                .sorted(Comparator.comparing(RecycleBinEntry::deletedAt).reversed())
                .toList();
    }

    public void restore(String resource, UUID id) {
        switch (resource) {
            case "vendors" -> vendorService.restore(id);
            case "agents" -> agentService.restore(id);
            case "farmers" -> farmerService.restore(id);
            case "policy-plans" -> planService.restore(id);
            case "policies" -> policyService.restore(id);
            default -> throw invalidResource(resource);
        }
    }

    public void purge(String resource, UUID id) {
        switch (resource) {
            case "vendors" -> vendorService.purge(id);
            case "agents" -> agentService.purge(id);
            case "farmers" -> farmerService.purge(id);
            case "policy-plans" -> planService.purge(id);
            case "policies" -> policyService.purge(id);
            default -> throw invalidResource(resource);
        }
    }

    private ApiException invalidResource(String resource) {
        return new ApiException(HttpStatus.BAD_REQUEST, "unsupported_resource",
                "Unsupported recycle-bin resource: " + resource);
    }
}
