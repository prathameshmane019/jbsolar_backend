package com.prathamesh.jbsolar.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prathamesh.jbsolar.api.dto.RecycleBinEntry;
import com.prathamesh.jbsolar.service.RecycleBinService;

@RestController
@RequestMapping("/api/v1/recycle-bin")
@PreAuthorize("hasRole('ADMIN')")
public class RecycleBinController {
    private final RecycleBinService service;

    public RecycleBinController(RecycleBinService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecycleBinEntry> list() {
        return service.list();
    }

    @PostMapping("/{resource}/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable String resource, @PathVariable UUID id) {
        service.restore(resource, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{resource}/{id}")
    public ResponseEntity<Void> purge(@PathVariable String resource, @PathVariable UUID id) {
        service.purge(resource, id);
        return ResponseEntity.noContent().build();
    }
}
