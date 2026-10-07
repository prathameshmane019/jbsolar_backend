package com.prathamesh.jbsolar.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.prathamesh.jbsolar.domain.RecordStatus;

import com.prathamesh.jbsolar.api.dto.VendorRequest;
import com.prathamesh.jbsolar.api.dto.VendorResponse;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.VendorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/vendors")
@Validated
public class VendorController {
    private final VendorService service;
    public VendorController(VendorService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VendorResponse> create(@Valid @RequestBody VendorRequest request) {
        VendorResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/vendors/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<VendorResponse> list(@ModelAttribute DataQuery query) { return service.search(query); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'VENDOR_AGENT')")
    public VendorResponse get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.get(id, principal);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public VendorResponse update(@PathVariable UUID id, @Valid @RequestBody VendorRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public VendorResponse setStatus(@PathVariable UUID id, @RequestParam RecordStatus status) {
        return service.setStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
         service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
