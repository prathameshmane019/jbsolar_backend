package com.prathamesh.jbsolar.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.prathamesh.jbsolar.api.dto.AgentRequest;
import com.prathamesh.jbsolar.api.dto.AgentResponse;
import com.prathamesh.jbsolar.api.dto.AgentUpdateRequest;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.AgentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/agents")
@Validated
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentResponse> create(
            @Valid @RequestBody AgentRequest request) {

        AgentResponse created = service.create(request);

        return ResponseEntity
                .created(URI.create("/api/v1/agents/" + created.id()))
                .body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDOR_AGENT')")
    public PageResponse<AgentResponse> list(@ModelAttribute DataQuery query,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.search(query, principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentResponse> findById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(service.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AgentResponse update(@PathVariable UUID id, @Valid @RequestBody AgentUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id) {

        service.delete(id);

        return ResponseEntity.noContent().build();
    }
}