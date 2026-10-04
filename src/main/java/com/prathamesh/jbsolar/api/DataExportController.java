package com.prathamesh.jbsolar.api;

import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prathamesh.jbsolar.service.DataExportService;
import com.prathamesh.jbsolar.api.dto.DataQuery;

@RestController
@RequestMapping("/api/v1/exports")
@PreAuthorize("hasRole('ADMIN')")
public class DataExportController {
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DataExportService service;

    public DataExportController(DataExportService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<byte[]> exportAll(@ModelAttribute DataQuery query) {
        return respond("all", query);
    }

    @GetMapping("/{resource}")
    public ResponseEntity<byte[]> export(@PathVariable String resource, @ModelAttribute DataQuery query) {
        return respond(resource, query);
    }

    private ResponseEntity<byte[]> respond(String resource, DataQuery query) {
        byte[] content = service.export(resource, query);
        String filename = "jbsolar-" + resource + "-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }
}
