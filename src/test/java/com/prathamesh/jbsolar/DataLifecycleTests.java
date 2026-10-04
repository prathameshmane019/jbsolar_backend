package com.prathamesh.jbsolar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;

import com.prathamesh.jbsolar.api.dto.VendorRequest;
import com.prathamesh.jbsolar.api.dto.AgentRequest;
import com.prathamesh.jbsolar.api.dto.DataQuery;
import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.service.AgentService;
import com.prathamesh.jbsolar.domain.RecordStatus;
import com.prathamesh.jbsolar.service.DataExportService;
import com.prathamesh.jbsolar.service.RecycleBinService;
import com.prathamesh.jbsolar.service.VendorService;

@SpringBootTest
@ActiveProfiles("test")
class DataLifecycleTests {
    @Autowired
    private VendorService vendorService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private RecycleBinService recycleBinService;

    @Autowired
    private DataExportService dataExportService;

    @Test
    void vendorCanBeRecycledAndRestoredWithoutDeletingItsData() {
        var vendor = vendorService.create(new VendorRequest("Lifecycle " + UUID.randomUUID(),
                "Test Contact", "9000000000", "lifecycle@example.com"));

        vendorService.delete(vendor.id());

        assertFalse(vendorService.list().stream().anyMatch(row -> row.id().equals(vendor.id())));
        assertTrue(recycleBinService.list().stream()
                .anyMatch(row -> row.resource().equals("vendors") && row.id().equals(vendor.id())));

        recycleBinService.restore("vendors", vendor.id());

        assertTrue(vendorService.list().stream().anyMatch(row -> row.id().equals(vendor.id())));
    }

    @Test
    void allDataExportIsAnExcelWorkbookWithEverySupportedResource() throws Exception {
        byte[] export = dataExportService.export("all", new DataQuery(null, null, null, null,
                null, null, null, null, null));

        assertTrue(export.length > 4);
        assertArrayEquals(new byte[] { (byte) 'P', (byte) 'K' }, new byte[] { export[0], export[1] });
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(export))) {
            assertEquals(5, workbook.getNumberOfSheets());
            assertEquals("Vendors", workbook.getSheetName(0));
            assertEquals("Agents", workbook.getSheetName(1));
            assertEquals("Farmers", workbook.getSheetName(2));
            assertEquals("Policy plans", workbook.getSheetName(3));
            assertEquals("Policies", workbook.getSheetName(4));
        }
    }

    @Test
    void vendorPurgeIsRejectedWhileJoinedAgentHistoryExists() {
        var vendor = vendorService.create(new VendorRequest("Joined " + UUID.randomUUID(),
                "Test Contact", "9000000002", "joined@example.com"));
        agentService.create(new AgentRequest(vendor.id(), "Joined Agent", "9000000003",
                "long-enough-test-password"));
        vendorService.delete(vendor.id());

        ApiException exception = assertThrows(ApiException.class,
                () -> recycleBinService.purge("vendors", vendor.id()));

        assertEquals("record_has_dependencies", exception.getCode());
        assertTrue(recycleBinService.list().stream()
                .anyMatch(row -> row.resource().equals("vendors") && row.id().equals(vendor.id())));
    }

    @Test
    void vendorPaginationAndStatusFiltersAreAppliedByTheRepository() {
        var inactive = vendorService.create(new VendorRequest("Filtered A " + UUID.randomUUID(),
                "Test Contact", "9000000010", "filtered@example.com"));
        var secondInactive = vendorService.create(new VendorRequest("Filtered B " + UUID.randomUUID(),
                "Test Contact", "9000000012", "filtered-second@example.com"));
        vendorService.setStatus(inactive.id(), RecordStatus.INACTIVE);
        vendorService.setStatus(secondInactive.id(), RecordStatus.INACTIVE);

        var page = vendorService.search(new DataQuery(0, 1, "name", "asc", "Filtered",
                "INACTIVE", null, null, null));
        var secondPage = vendorService.search(new DataQuery(1, 1, "name", "asc", "Filtered",
                "INACTIVE", null, null, null));

        assertEquals(1, page.content().size());
        assertEquals(inactive.id(), page.content().getFirst().id());
        assertEquals(secondInactive.id(), secondPage.content().getFirst().id());
        assertEquals(2, page.totalElements());
        assertEquals(2, page.totalPages());
    }

    @Test
    void vendorExcelExportHonorsStatusAndSearchFilters() throws Exception {
        var inactive = vendorService.create(new VendorRequest("ExportFilter " + UUID.randomUUID(),
                "Test Contact", "9000000011", "export-filter@example.com"));
        vendorService.setStatus(inactive.id(), RecordStatus.INACTIVE);

        byte[] export = dataExportService.export("vendors",
                new DataQuery(null, 1, "name", "asc", "ExportFilter", "INACTIVE", null, null, null));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(export))) {
            var sheet = workbook.getSheet("Vendors");
            assertEquals(2, sheet.getPhysicalNumberOfRows());
            assertTrue(sheet.getRow(1).getCell(0).getStringCellValue().contains(inactive.id().toString()));
        }
    }

    @Test
    void allDataExportSupportsStatusValuesThatOnlyApplyToSomeSheets() throws Exception {
        byte[] export = dataExportService.export("all", new DataQuery(null, null, null, null,
                null, "PENDING", null, null, null));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(export))) {
            assertEquals(1, workbook.getSheet("Vendors").getPhysicalNumberOfRows());
            assertEquals(1, workbook.getSheet("Agents").getPhysicalNumberOfRows());
            assertEquals(1, workbook.getSheet("Policy plans").getPhysicalNumberOfRows());
            assertEquals(1, workbook.getSheet("Policies").getPhysicalNumberOfRows());
        }
    }
}
