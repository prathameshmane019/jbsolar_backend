package com.prathamesh.jbsolar.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.domain.Farmer;
import com.prathamesh.jbsolar.domain.Policy;
import com.prathamesh.jbsolar.domain.PolicyPlan;
import com.prathamesh.jbsolar.domain.Vendor;
import com.prathamesh.jbsolar.domain.VendorAgent;
import com.prathamesh.jbsolar.repository.FarmerRepository;
import com.prathamesh.jbsolar.repository.PolicyPlanRepository;
import com.prathamesh.jbsolar.repository.PolicyRepository;
import com.prathamesh.jbsolar.repository.VendorAgentRepository;
import com.prathamesh.jbsolar.repository.VendorRepository;

@Service
public class DataExportService {
    private static final int PAGE_SIZE = 500;
    private static final int MAX_ROWS_PER_SHEET = 100_000;

    private final VendorRepository vendors;
    private final VendorAgentRepository agents;
    private final FarmerRepository farmers;
    private final PolicyPlanRepository plans;
    private final PolicyRepository policies;

    public DataExportService(VendorRepository vendors, VendorAgentRepository agents, FarmerRepository farmers,
            PolicyPlanRepository plans, PolicyRepository policies) {
        this.vendors = vendors;
        this.agents = agents;
        this.farmers = farmers;
        this.plans = plans;
        this.policies = policies;
    }

    @Transactional(readOnly = true)
    public byte[] export(String requestedResource, DataQuery requestedQuery) {
        String resource = requestedResource == null ? "all" : requestedResource.toLowerCase(Locale.ROOT);
        DataQuery query = requestedQuery == null
                ? new DataQuery(0, PAGE_SIZE, null, null, null, null, null, null, null)
                : requestedQuery;
        if ("all".equals(resource) && query.sort() != null && !query.sort().isBlank()
                && !Set.of("createdAt", "updatedAt", "id", "status").contains(query.sort())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_sort_field",
                    "All-data exports support sorting by createdAt, updatedAt, id, or status");
        }
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try (workbook;
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            switch (resource) {
                case "all" -> {
                    writeVendors(workbook, query, true);
                    writeAgents(workbook, query, true);
                    writeFarmers(workbook, withSort(query,
                            "status".equals(query.sort()) ? "createdAt" : query.sort()));
                    writePlans(workbook, query, true);
                    writePolicies(workbook, query, true);
                }
                case "vendors" -> writeVendors(workbook, query, false);
                case "agents" -> writeAgents(workbook, query, false);
                case "farmers" -> writeFarmers(workbook, query);
                case "policy-plans" -> writePlans(workbook, query, false);
                case "policies" -> writePolicies(workbook, query, false);
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "unsupported_export_resource",
                        "Unsupported export resource: " + requestedResource);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate the Excel export", exception);
        } finally {
            workbook.dispose();
        }
    }

    private void writeVendors(SXSSFWorkbook workbook, DataQuery query, boolean ignoreUnsupportedStatus) {
        Sheet sheet = workbook.createSheet("Vendors");
        header(sheet, "ID", "Name", "Contact person", "Mobile", "Email", "Status", "Created at");
        int row = 1;
        int pageNumber = 0;
        Page<Vendor> page;
        Pageable pageable = pageable(query, Set.of("name", "mobile", "email", "status", "createdAt", "updatedAt"));
        do {
            page = vendors.findAll(DataQuerySupport.specification(query,
                            java.util.List.of("name", "contactPerson", "mobile", "email"), true, null, false,
                            ignoreUnsupportedStatus),
                    PageRequest.of(pageNumber++, pageable.getPageSize(), pageable.getSort()));
            for (Vendor vendor : page.getContent()) {
                ensureLimit(row);
                append(sheet, row++, vendor.getId(), vendor.getName(), vendor.getContactPerson(),
                        vendor.getMobile(), vendor.getEmail(), vendor.getStatus(), vendor.getCreatedAt());
            }
        } while (page.hasNext());
    }

    private void writeAgents(SXSSFWorkbook workbook, DataQuery query, boolean ignoreUnsupportedStatus) {
        Sheet sheet = workbook.createSheet("Agents");
        header(sheet, "ID", "User ID", "Vendor ID", "Vendor", "Full name", "Mobile", "Status", "Created at");
        int row = 1;
        int pageNumber = 0;
        Page<VendorAgent> page;
        Pageable pageable = pageable(query, Set.of("fullName", "user.mobile", "vendor.name", "status",
                "createdAt", "updatedAt"));
        do {
            page = agents.findAll(DataQuerySupport.specification(query,
                            java.util.List.of("fullName", "user.mobile", "vendor.name"), true, "vendor.id", false,
                            ignoreUnsupportedStatus),
                    PageRequest.of(pageNumber++, pageable.getPageSize(), pageable.getSort()));
            for (VendorAgent agent : page.getContent()) {
                ensureLimit(row);
                append(sheet, row++, agent.getId(), agent.getUser().getId(), agent.getVendor().getId(),
                        agent.getVendor().getName(), agent.getFullName(), agent.getUser().getMobile(),
                        agent.getStatus(), agent.getCreatedAt());
            }
        } while (page.hasNext());
    }

    private void writeFarmers(SXSSFWorkbook workbook, DataQuery query) {
        Sheet sheet = workbook.createSheet("Farmers");
        header(sheet, "ID", "Customer code", "Full name", "Mobile", "Address", "District", "Taluka",
                "Village", "Agent ID", "Created at");
        int row = 1;
        int pageNumber = 0;
        Page<Farmer> page;
        Pageable pageable = pageable(query, Set.of("customerCode", "fullName", "mobile", "district",
                "taluka", "village", "createdAt", "updatedAt"));
        do {
            page = farmers.findAll(DataQuerySupport.specification(query,
                            java.util.List.of("customerCode", "fullName", "mobile", "address", "district",
                                    "taluka", "village"), false, "createdBy.vendor.id", true),
                    PageRequest.of(pageNumber++, pageable.getPageSize(), pageable.getSort()));
            for (Farmer farmer : page.getContent()) {
                ensureLimit(row);
                append(sheet, row++, farmer.getId(), farmer.getCustomerCode(), farmer.getFullName(),
                        farmer.getMobile(), farmer.getAddress(), farmer.getDistrict(), farmer.getTaluka(),
                        farmer.getVillage(), farmer.getCreatedBy() == null ? null : farmer.getCreatedBy().getId(),
                        farmer.getCreatedAt());
            }
        } while (page.hasNext());
    }

    private void writePlans(SXSSFWorkbook workbook, DataQuery query, boolean ignoreUnsupportedStatus) {
        Sheet sheet = workbook.createSheet("Policy plans");
        header(sheet, "ID", "Name", "Description", "Duration months", "Price", "GST percentage",
                "Terms and conditions", "Status", "Created at");
        int row = 1;
        int pageNumber = 0;
        Page<PolicyPlan> page;
        Pageable pageable = pageable(query, Set.of("name", "price", "durationMonths", "gstPercentage", "status",
                "createdAt", "updatedAt"));
        do {
            page = plans.findAll(DataQuerySupport.specification(query,
                            java.util.List.of("name", "description", "termsAndConditions"), true, null, false,
                            ignoreUnsupportedStatus),
                    PageRequest.of(pageNumber++, pageable.getPageSize(), pageable.getSort()));
            for (PolicyPlan plan : page.getContent()) {
                ensureLimit(row);
                append(sheet, row++, plan.getId(), plan.getName(), plan.getDescription(),
                        plan.getDurationMonths(), plan.getPrice(), plan.getGstPercentage(),
                        plan.getTermsAndConditions(), plan.getStatus(), plan.getCreatedAt());
            }
        } while (page.hasNext());
    }

    private void writePolicies(SXSSFWorkbook workbook, DataQuery query, boolean ignoreUnsupportedStatus) {
        Sheet sheet = workbook.createSheet("Policies");
        header(sheet, "ID", "Policy number", "Farmer ID", "Farmer", "Plan ID", "Plan", "Vendor ID",
                "Start date", "End date", "Amount", "GST", "Total amount", "Status", "Created at");
        int row = 1;
        int pageNumber = 0;
        Page<Policy> page;
        Pageable pageable = pageable(query, Set.of("policyNumber", "startDate", "endDate", "amount",
                "totalAmount", "status", "createdAt", "updatedAt"));
        do {
            page = policies.findAll(DataQuerySupport.specification(query,
                            java.util.List.of("policyNumber", "farmer.fullName", "plan.name"), true, "vendor.id", false,
                            ignoreUnsupportedStatus),
                    PageRequest.of(pageNumber++, pageable.getPageSize(), pageable.getSort()));
            for (Policy policy : page.getContent()) {
                ensureLimit(row);
                append(sheet, row++, policy.getId(), policy.getPolicyNumber(), policy.getFarmer().getId(),
                        policy.getFarmer().getFullName(), policy.getPlan().getId(), policy.getPlan().getName(),
                        policy.getVendor().getId(), policy.getStartDate(), policy.getEndDate(),
                        policy.getAmount(), policy.getGstAmount(), policy.getTotalAmount(),
                        policy.getStatus(), policy.getCreatedAt());
            }
        } while (page.hasNext());
    }

    private Pageable pageable(DataQuery query, Set<String> sortableFields) {
        DataQuery exportQuery = new DataQuery(0,
                query.size() == null ? PAGE_SIZE : query.size(),
                query.sort(), query.direction(), query.search(), query.status(),
                query.createdFrom(), query.createdTo(), query.vendorId());
        return DataQuerySupport.pageable(exportQuery, sortableFields);
    }

    private DataQuery withSort(DataQuery query, String sort) {
        return new DataQuery(0, query.size(), sort, query.direction(), query.search(), query.status(),
                query.createdFrom(), query.createdTo(), query.vendorId());
    }

    private void header(Sheet sheet, String... titles) {
        Row row = sheet.createRow(0);
        for (int column = 0; column < titles.length; column++) {
            row.createCell(column).setCellValue(titles[column]);
        }
        row.setHeightInPoints(20);
        sheet.createFreezePane(0, 1);
        sheet.setDefaultColumnWidth(20);
    }

    private void append(Sheet sheet, int rowNumber, Object... values) {
        Row row = sheet.createRow(rowNumber);
        for (int column = 0; column < values.length; column++) {
            Cell cell = row.createCell(column);
            Object value = values[column];
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof UUID || value instanceof Instant
                    || value instanceof java.time.temporal.TemporalAccessor) {
                cell.setCellValue(value.toString());
            } else if (value instanceof Enum<?> enumValue) {
                cell.setCellValue(enumValue.name());
            } else {
                cell.setCellValue(value.toString());
            }
        }
    }

    private void ensureLimit(int zeroBasedRow) {
        if (zeroBasedRow >= MAX_ROWS_PER_SHEET + 1) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "export_too_large",
                    "Excel export is limited to 100,000 records per sheet");
        }
    }
}
