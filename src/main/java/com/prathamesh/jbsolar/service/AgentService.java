package com.prathamesh.jbsolar.service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.AgentRequest;
import com.prathamesh.jbsolar.api.dto.AgentResponse;
import com.prathamesh.jbsolar.api.dto.AgentUpdateRequest;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.domain.User;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.domain.VendorAgent;
import com.prathamesh.jbsolar.repository.FarmerRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.repository.UserRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.repository.VendorRepository;

@Service
@Transactional
public class AgentService {

    private final VendorAgentRepository agents;
    private final UserRepository users;
    private final VendorRepository vendors;
    private final FarmerRepository farmers;
    private final PolicyRepository policies;
    private final PasswordEncoder encoder;

    public AgentService(
            VendorAgentRepository agents,
            UserRepository users,
            VendorRepository vendors,
            FarmerRepository farmers,
            PolicyRepository policies,
            PasswordEncoder encoder) {

        this.agents = agents;
        this.users = users;
        this.vendors = vendors;
        this.farmers = farmers;
        this.policies = policies;
        this.encoder = encoder;
    }

    public AgentResponse create(AgentRequest request) {

        String mobile = request.mobile().trim();
        if (users.existsByMobile(mobile)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "mobile_already_registered",
                    "A user with this mobile number already exists"
            );
        }

        var vendor = vendors.findById(request.vendorId())
                .filter(row -> row.getDeletedAt() == null && row.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "vendor_not_found",
                        "Vendor was not found"
                ));

        User user = new User();
        user.setMobile(mobile);
        user.setPasswordHash(encoder.encode(request.initialPassword()));
        user.setRole(UserRole.VENDOR_AGENT);

        users.save(user);

        VendorAgent agent = new VendorAgent();
        agent.setUser(user);
        agent.setVendor(vendor);
        agent.setFullName(request.fullName().trim());

        agents.save(agent);

        return toResponse(agent);
    }

    public AgentResponse update(UUID id, AgentUpdateRequest request) {
        VendorAgent agent = requireActiveAgent(id);
        String mobile = request.mobile().trim();
        if (users.existsByMobileAndIdNot(mobile, agent.getUser().getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "mobile_already_registered",
                    "A user with this mobile number already exists");
        }
        var vendor = vendors.findById(request.vendorId())
                .filter(row -> row.getDeletedAt() == null && row.getStatus() == RecordStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "vendor_not_found",
                        "Vendor was not found"));
        if (!agent.getVendor().getId().equals(vendor.getId())
                && (farmers.countByCreatedById(id) > 0 || policies.countByCreatedById(id) > 0)) {
            throw new ApiException(HttpStatus.CONFLICT, "agent_vendor_has_history",
                    "An agent with farmer or policy history cannot be moved to another vendor");
        }
        agent.setVendor(vendor);
        agent.setFullName(request.fullName().trim());
        agent.setStatus(request.status());
        agent.getUser().setMobile(mobile);
        return toResponse(agent);
    }

    @Transactional(readOnly = true)
    public AgentResponse findById(UUID id) {

        VendorAgent agent = agents.findById(id).filter(row -> row.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "agent_not_found",
                        "Agent was not found"
                ));

        return toResponse(agent);
    }

    public void delete(UUID id) {

        requireActiveAgent(id).setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list(UUID vendorId) {

        return agents.findAll().stream()
                .filter(agent -> agent.getDeletedAt() == null)
                .filter(agent ->
                        vendorId == null ||
                                vendorId.equals(agent.getVendor().getId())
                )
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentResponse> search(DataQuery query) {
        var page = agents.findAll(DataQuerySupport.specification(query,
                        List.of("fullName", "user.mobile", "vendor.name"), true, "vendor.id", false),
                DataQuerySupport.pageable(query,
                        Set.of("fullName", "user.mobile", "vendor.name", "status", "createdAt", "updatedAt")));
        return PageResponse.from(page.map(this::toResponse));
    }

    private VendorAgent requireActiveAgent(UUID id) {
        return agents.findById(id).filter(agent -> agent.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "agent_not_found",
                        "Agent was not found"));
    }

    public void purge(UUID id) {
        VendorAgent agent = agents.findById(id).filter(row -> row.getDeletedAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "deleted_agent_not_found",
                        "Deleted agent was not found"));
        if (farmers.countByCreatedById(id) > 0 || policies.countByCreatedById(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "record_has_dependencies",
                    "Agent is referenced by farmer or policy history and cannot be permanently deleted");
        }
        agents.delete(agent);
        agents.flush();
        users.delete(agent.getUser());
    }

    public void restore(UUID id) {
        VendorAgent agent = agents.findById(id).filter(row -> row.getDeletedAt() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "deleted_agent_not_found",
                        "Deleted agent was not found"));
        agent.setDeletedAt(null);
    }

    private AgentResponse toResponse(VendorAgent agent) {

        return new AgentResponse(
                agent.getId(),
                agent.getUser().getId(),
                agent.getVendor().getId(),
                agent.getVendor().getName(),
                agent.getFullName(),
                agent.getUser().getMobile(),
                agent.getStatus()
        );
    }
}