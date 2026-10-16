package com.prathamesh.jbsolar.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.prathamesh.jbsolar.api.dto.FileMetadataResponse;
import com.prathamesh.jbsolar.api.dto.UploadUrlRequest;
import com.prathamesh.jbsolar.api.dto.UploadUrlResponse;
import com.prathamesh.jbsolar.domain.UploadResourceType;
import com.prathamesh.jbsolar.security.UserPrincipal;
import com.prathamesh.jbsolar.service.MobileFileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/mobile/files")
@Validated
@PreAuthorize("hasRole('VENDOR_AGENT')")
public class MobileFileController {
    private final MobileFileService files;

    public MobileFileController(MobileFileService files) {
        this.files = files;
    }

    @PostMapping("/upload-url")
    public UploadUrlResponse createUploadUrl(@Valid @RequestBody UploadUrlRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return files.createUploadUrl(request, principal);
    }

    @PostMapping("/{id}/complete")
    public FileMetadataResponse completeUpload(@PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return files.completeUpload(id, principal);
    }

    @GetMapping
    public List<FileMetadataResponse> listForResource(
            @RequestParam UploadResourceType resourceType,
            @RequestParam UUID resourceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return files.listForResource(resourceType, resourceId, principal);
    }
}
