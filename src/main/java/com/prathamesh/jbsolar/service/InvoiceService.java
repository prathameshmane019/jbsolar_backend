package com.prathamesh.jbsolar.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
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
import com.prathamesh.jbsolar.api.dto.InvoiceResponse;
import com.prathamesh.jbsolar.api.dto.PageResponse;
import com.prathamesh.jbsolar.domain.Invoice;
import com.prathamesh.jbsolar.domain.Payment;
import com.prathamesh.jbsolar.domain.Policy;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.repository.InvoiceRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.security.UserPrincipal;

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final PolicyRepository policies;

    public InvoiceService(InvoiceRepository invoices, PolicyRepository policies) {
        this.invoices = invoices;
        this.policies = policies;
    }
    public void createForPayment(Policy policy, Payment payment) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber("INV-" + UUID.randomUUID().toString()
                .substring(0, 12).toUpperCase(java.util.Locale.ROOT));
        invoice.setPolicy(policy);
        invoice.setPayment(payment);
        invoice.setInvoiceDate(LocalDate.now(ZoneOffset.UTC));
        invoice.setAmount(policy.getAmount());
        invoice.setGstAmount(policy.getGstAmount());
        invoice.setTotalAmount(policy.getTotalAmount());
        invoices.save(invoice);
    }

    @Transactional(readOnly = true)
    public UUID findIdByPolicyId(UUID policyId) {
        return invoices.findByPolicyId(policyId).map(Invoice::getId).orElse(null);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID invoiceId, UserPrincipal principal) {
        var invoice = principal.role() == UserRole.ADMIN
                ? invoices.findById(invoiceId)
                : invoices.findByIdAndPolicyVendorId(invoiceId, principal.vendorId());
        return invoice.map(this::toResponse).orElseThrow(this::invoiceNotFound);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(DataQuery query, UserPrincipal principal) {
        if (query.createdFrom() != null && query.createdTo() != null
                && query.createdFrom().isAfter(query.createdTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_date_range",
                    "createdFrom must be on or before createdTo");
        }
        if (query.status() != null && !query.status().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "status_filter_unsupported",
                    "Status filtering is not supported for invoices");
        }

        var pageable = DataQuerySupport.pageable(query,
                Set.of("createdAt", "invoiceDate", "invoiceNumber", "totalAmount"));
        Specification<Invoice> specification = (root, criteria, builder) -> {
            var policy = root.join("policy");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(policy.get("deletedAt")));

            if (principal.role() != UserRole.ADMIN) {
                predicates.add(builder.equal(policy.get("vendor").get("id"), principal.vendorId()));
            }
            if (query.vendorId() != null) {
                predicates.add(builder.equal(policy.get("vendor").get("id"), query.vendorId()));
            }
            if (query.search() != null && !query.search().isBlank()) {
                String term = "%" + query.search().trim().toLowerCase(java.util.Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("invoiceNumber")), term, '\\'),
                        builder.like(builder.lower(policy.get("policyNumber")), term, '\\')));
            }
            if (query.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"),
                        query.createdFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (query.createdTo() != null) {
                predicates.add(builder.lessThan(root.get("createdAt"),
                        query.createdTo().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        var page = invoices.findAll(specification, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getForPolicy(UUID policyId, UserPrincipal principal) {
        var policy = policies.findById(policyId)
                .filter(row -> row.getDeletedAt() == null && canAccess(row.getVendor().getId(), principal))
                .orElseThrow(this::policyNotFound);
        return invoices.findByPolicyId(policy.getId()).map(this::toResponse)
                .orElseThrow(this::invoiceNotFound);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        var policy = invoice.getPolicy();
        var farmer = policy.getFarmer();
        var plan = policy.getPlan();
        var vendor = policy.getVendor();
        var payment = invoice.getPayment();
        String farmerAddress = java.util.stream.Stream.of(farmer.getAddress(), farmer.getVillage(),
                farmer.getTaluka(), farmer.getDistrict())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), policy.getId(),
                policy.getPolicyNumber(), payment.getId(), invoice.getInvoiceDate(),
                invoice.getAmount(), invoice.getGstAmount(), invoice.getTotalAmount(), invoice.getCreatedAt(),
                policy.getStartDate(), policy.getEndDate(), policy.getPumpPowerHp(), policy.getMotorHeadMeters(),
                plan.getName(), plan.getPlanCode(), plan.getDurationMonths(), plan.getGstPercentage(),
                farmer.getFullName(), farmer.getCustomerCode(), farmer.getMobile(), farmerAddress,
                vendor.getName(), vendor.getVendorCode(), vendor.getMobile(), vendor.getEmail(),
                payment.getPaymentNumber(), payment.getPaymentMethod(), payment.getGateway(),
                payment.getGatewayPaymentId(), payment.getPaidAt());
    }

    private boolean canAccess(UUID vendorId, UserPrincipal principal) {
        return principal.role() == UserRole.ADMIN || vendorId.equals(principal.vendorId());
    }

    private ApiException policyNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "policy_not_found", "Policy was not found");
    }

    private ApiException invoiceNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "invoice_not_found", "Invoice was not found");
    }
}
