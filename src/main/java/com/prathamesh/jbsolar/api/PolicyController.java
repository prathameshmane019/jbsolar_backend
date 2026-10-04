package com.prathamesh.jbsolar.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.PolicyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/policies")
@Validated
public class PolicyController {
    private final PolicyService service;
    public PolicyController(PolicyService service) { this.service = service; }

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
