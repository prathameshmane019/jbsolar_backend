package com.prathamesh.jbsolar.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.InvoiceResponse;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.InvoiceService;

@RestController
@RequestMapping("/api/v1/invoices")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDOR_AGENT')")
public class InvoiceController {
    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @GetMapping
    public PageResponse<InvoiceResponse> list(@ModelAttribute DataQuery query,
            @AuthenticationPrincipal UserPrincipal principal) {
        return invoices.search(query, principal);
    }

    @GetMapping("/{invoiceId}")
    public InvoiceResponse get(@PathVariable UUID invoiceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return invoices.get(invoiceId, principal);
    }
}
