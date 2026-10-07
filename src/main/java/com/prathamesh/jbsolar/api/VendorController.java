package com.prathamesh.jbsolar.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.prathamesh.jbsolar.domain.RecordStatus;

import com.prathamesh.jbsolar.api.dto.VendorRequest;
import com.prathamesh.jbsolar.api.dto.VendorResponse;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.service.VendorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/vendors")
@Validated
@PreAuthorize("hasRole('ADMIN')")
public class VendorController {
    private final VendorService service;
    public VendorController(VendorService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<VendorResponse> create(@Valid @RequestBody VendorRequest request) {
        VendorResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/vendors/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<VendorResponse> list(@ModelAttribute DataQuery query) { return service.search(query); }

    @GetMapping("/{id}")
    public VendorResponse get(@PathVariable UUID id) { return service.get(id); }

    @PutMapping("/{id}")
    public VendorResponse update(@PathVariable UUID id, @Valid @RequestBody VendorRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public VendorResponse setStatus(@PathVariable UUID id, @RequestParam RecordStatus status) {
        return service.setStatus(id, status);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
         service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
