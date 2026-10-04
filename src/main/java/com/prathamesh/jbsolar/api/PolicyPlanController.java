package com.prathamesh.jbsolar.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.prathamesh.jbsolar.api.dto.PolicyPlanRequest;
import com.prathamesh.jbsolar.api.dto.PolicyPlanResponse;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.service.PolicyPlanService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/policy-plans")
@Validated
public class PolicyPlanController {
    private final PolicyPlanService service;
    public PolicyPlanController(PolicyPlanService service) { this.service = service; }

    @GetMapping
    public PageResponse<PolicyPlanResponse> list(@ModelAttribute DataQuery query,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.search(query, activeOnly);
    }

    @GetMapping("/{id}")
    public PolicyPlanResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PolicyPlanResponse> create(@Valid @RequestBody PolicyPlanRequest request) {
        PolicyPlanResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/policy-plans/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyPlanResponse update(@PathVariable UUID id, @Valid @RequestBody PolicyPlanRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyPlanResponse setStatus(@PathVariable UUID id, @RequestParam RecordStatus status) {
        return service.setStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
