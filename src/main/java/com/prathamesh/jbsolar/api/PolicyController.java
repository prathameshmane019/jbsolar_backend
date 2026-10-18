package com.prathamesh.jbsolar.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prathamesh.jbsolar.api.dto.PolicyRequest;
import com.prathamesh.jbsolar.api.dto.PolicyResponse;
import com.prathamesh.jbsolar.api.dto.FileMetadataResponse;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.PolicyService;
import com.prathamesh.jbsolar.service.MobileFileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/policies")
@Validated
public class PolicyController {
    private final PolicyService service;
    private final MobileFileService files;
    public PolicyController(PolicyService service, MobileFileService files) {
        this.service = service;
        this.files = files;
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasRole('ADMIN')")
    public List<FileMetadataResponse> files(@PathVariable UUID id) {
        return files.listForPolicy(id);
    }

    @GetMapping
    public PageResponse<PolicyResponse> list(@ModelAttribute DataQuery query,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.search(query, principal);
    }

    @GetMapping("/{id}")
    public PolicyResponse get(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return service.get(id, principal);
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody PolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
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
        PolicyResponse created = service.create(request, principal);
        return ResponseEntity.created(URI.create("/api/v1/policies/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public PolicyResponse update(@PathVariable UUID id, @Valid @RequestBody PolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.update(id, request, principal);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
