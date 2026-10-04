package com.prathamesh.jbsolar.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DataQuery(Integer page, Integer size, String sort, String direction, String search,
        String status, LocalDate createdFrom, LocalDate createdTo, UUID vendorId) {
}
