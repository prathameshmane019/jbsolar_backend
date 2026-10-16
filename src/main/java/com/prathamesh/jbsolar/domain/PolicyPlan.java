package com.prathamesh.jbsolar.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "policy_plans",
        indexes = {
                @Index(
                        name = "policy_plans_deleted_at_index",
                        columnList = "deleted_at"
                )
        }
)
@Data
public class PolicyPlan {

    @Id
    @UuidGenerator
    @Column(
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "plan_code",
            nullable = false,
            unique = true,
            length = 30,
            insertable = false,
            updatable = false
    )
    private String planCode;

    @Column(
            nullable = false,
            length = 150
    )
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(
            name = "duration_months",
            nullable = false
    )
    private int durationMonths;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal price;

    @Column(
            name = "gst_percentage",
            nullable = false,
            precision = 5,
            scale = 2
    )
    private BigDecimal gstPercentage;

    @Column(
            name = "terms_and_conditions",
            columnDefinition = "text"
    )
    private String termsAndConditions;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 255
    )
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (status == null) {
            status = RecordStatus.ACTIVE;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}