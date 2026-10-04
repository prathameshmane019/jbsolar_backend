package com.prathamesh.jbsolar.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RecycleBinEntry(String resource, UUID id, String label, Instant deletedAt) {
}
