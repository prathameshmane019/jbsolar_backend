package com.prathamesh.jbsolar.api;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.InvoiceDetailResponse;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.InvoiceService;

// Copy the principal / authorization style from PolicyController if it differs.
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {
    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InvoiceDetailResponse> search(DataQuery query, @AuthenticationPrincipal UserPrincipal principal) {
        return service.search(query, principal);
    }

    @GetMapping("/{id}")
    public InvoiceDetailResponse get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return service.get(id, principal);
    }
}