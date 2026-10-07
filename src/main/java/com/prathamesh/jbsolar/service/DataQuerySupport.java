package com.prathamesh.jbsolar.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.prathamesh.jbsolar.api.ApiException;
import com.prathamesh.jbsolar.api.dto.DataQuery;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public final class DataQuerySupport {
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 500;

    private DataQuerySupport() {
    }

    public static Pageable pageable(DataQuery query, Set<String> sortableFields) {
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? DEFAULT_PAGE_SIZE : query.size();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_pagination",
                    "page must be non-negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String sortField = query.sort() == null || query.sort().isBlank() ? "createdAt" : query.sort();
        if (!sortableFields.contains(sortField)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_sort_field",
                    "Unsupported sort field: " + sortField);
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(
                    query.direction() == null || query.direction().isBlank() ? "desc" : query.direction());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_sort_direction",
                    "direction must be asc or desc");
        }
        Sort sort = Sort.by(direction, sortField).and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(page, size, sort);
    }

    public static <T> Specification<T> specification(DataQuery query, List<String> searchFields,
            boolean hasStatus, String vendorIdPath, boolean ignoreStatus) {
        return specification(query, searchFields, hasStatus, vendorIdPath, ignoreStatus, false);
    }

    public static <T> Specification<T> specification(DataQuery query, List<String> searchFields,
            boolean hasStatus, String vendorIdPath, boolean ignoreStatus, boolean ignoreUnsupportedStatus) {
        if (query.createdFrom() != null && query.createdTo() != null
                && query.createdFrom().isAfter(query.createdTo())) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_date_range",
                    "createdFrom must be on or before createdTo");
        }
        if (!ignoreStatus && query.status() != null && !query.status().isBlank() && !hasStatus) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "status_filter_unsupported",
                    "Status filtering is not supported for this resource");
        }
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));

            if (query.search() != null && !query.search().isBlank()) {
                String term = "%" + escapeLike(query.search().trim().toLowerCase(java.util.Locale.ROOT)) + "%";
                Predicate[] matches = searchFields.stream()
                        .map(field -> builder.like(
                                builder.lower(path(root, field).as(String.class)), term, '\\'))
                        .toArray(Predicate[]::new);
                predicates.add(builder.or(matches));
            }
            if (!ignoreStatus && query.status() != null && !query.status().isBlank() && hasStatus) {
                String requestedStatus = query.status().trim().toUpperCase(java.util.Locale.ROOT);
                Class<?> statusType = root.get("status").getJavaType();
                if (statusType.isEnum() && Arrays.stream(statusType.getEnumConstants())
                        .map(Enum.class::cast).map(Enum::name).noneMatch(requestedStatus::equals)) {
                    if (ignoreUnsupportedStatus) {
                        predicates.add(builder.disjunction());
                    } else {
                        throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "invalid_status",
                                "Unsupported status: " + query.status());
                    }
                } else {
                    predicates.add(builder.equal(root.get("status").as(String.class), requestedStatus));
                }
            }
            if (query.createdFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"),
                        query.createdFrom().atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (query.createdTo() != null) {
                LocalDate exclusiveEnd = query.createdTo().plusDays(1);
                predicates.add(builder.lessThan(root.get("createdAt"),
                        exclusiveEnd.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (query.vendorId() != null && vendorIdPath != null) {
                predicates.add(builder.equal(path(root, vendorIdPath), query.vendorId()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Path<?> path(Path<?> root, String dottedPath) {
        Path<?> result = root;
        for (String segment : dottedPath.split("\\.")) {
            result = result.get(segment);
        }
        return result;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
