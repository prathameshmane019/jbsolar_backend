package com.prathamesh.jbsolar.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.dto.InvoiceDetailResponse;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.domain.Farmer;
import com.prathamesh.jbsolar.domain.Invoice;
import com.prathamesh.jbsolar.domain.Payment;
import com.prathamesh.jbsolar.domain.Policy;
import com.prathamesh.jbsolar.domain.PolicyPlan;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.domain.Vendor;
import com.prathamesh.jbsolar.repository.InvoiceRepository;
import com.prathamesh.jbsolar.security.UserPrincipal;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly = true)
public class InvoiceService {
    private static final Set<String> SORTABLE =
            Set.of("invoiceNumber", "invoiceDate", "amount", "gstAmount", "totalAmount", "createdAt");

    private final InvoiceRepository invoices;

    public InvoiceService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    public PageResponse<InvoiceDetailResponse> search(DataQuery query, UserPrincipal principal) {
        UUID vendorId = principal.role() == UserRole.ADMIN ? query.vendorId() : principal.vendorId();
        var page = invoices.findAll(
                specification(query.search(), vendorId),
                DataQuerySupport.pageable(query, SORTABLE));
        return PageResponse.from(page.map(this::toResponse));
    }

    public InvoiceDetailResponse get(UUID id, UserPrincipal principal) {
        var found = principal.role() == UserRole.ADMIN
                ? invoices.findById(id)
                : invoices.findByIdAndPolicyVendorId(id, principal.vendorId());
        Invoice invoice = found
                .filter(row -> row.getPolicy().getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "invoice_not_found",
                        "Invoice was not found"));
        return toResponse(invoice);
    }

    private Specification<Invoice> specification(String search, UUID vendorId) {
        return (root, cq, cb) -> {
            Join<Invoice, Policy> policy = root.join("policy", JoinType.INNER);
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isNull(policy.get("deletedAt")));
            if (vendorId != null) {
                where.add(cb.equal(policy.get("vendor").get("id"), vendorId));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                Join<Policy, Farmer> farmer = policy.join("farmer", JoinType.INNER);
                where.add(cb.or(
                        cb.like(cb.lower(root.<String>get("invoiceNumber")), like),
                        cb.like(cb.lower(policy.<String>get("policyNumber")), like),
                        cb.like(cb.lower(farmer.<String>get("fullName")), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private InvoiceDetailResponse toResponse(Invoice i) {
        Policy p = i.getPolicy();
        Farmer f = p.getFarmer();
        PolicyPlan plan = p.getPlan();
        Vendor v = p.getVendor();
        Payment pay = i.getPayment();
        return new InvoiceDetailResponse(
                i.getId(), i.getInvoiceNumber(), i.getInvoiceDate(), i.getCreatedAt(),
                i.getAmount(), i.getGstAmount(), i.getTotalAmount(), gstPercentage(i),
                p.getId(), p.getPolicyNumber(), p.getStartDate(), p.getEndDate(),
                p.getPumpPowerHp(), p.getMotorHeadMeters(),
                plan.getName(), plan.getPlanCode(), plan.getDurationMonths(),
                f.getFullName(), f.getCustomerCode(), f.getMobile(), address(f),
                v.getName(), v.getVendorCode(), v.getMobile(), v.getEmail(),
                pay.getId(), pay.getPaymentNumber(), pay.getPaymentMethod(), pay.getGateway(),
                pay.getGatewayPaymentId(), pay.getPaidAt());
    }

    // Derived from the invoice itself, so it stays correct even if the plan's GST changes later.
    private BigDecimal gstPercentage(Invoice i) {
        if (i.getAmount() == null || i.getAmount().signum() == 0) return BigDecimal.ZERO;
        return i.getGstAmount().multiply(new BigDecimal("100"))
                .divide(i.getAmount(), 2, RoundingMode.HALF_UP);
    }

    private String address(Farmer f) {
        List<String> parts = new ArrayList<>();
        for (String s : new String[] {f.getAddress(), f.getVillage(), f.getTaluka(), f.getDistrict()}) {
            if (s != null && !s.isBlank()) parts.add(s.trim());
        }
        return String.join(", ", parts);
    }
}