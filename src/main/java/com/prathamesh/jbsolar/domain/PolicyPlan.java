package com.prathamesh.jbsolar.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.Data;
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

@Entity
@Table(name = "policy_plans", indexes = @Index(name = "policy_plans_deleted_at_index", columnList = "deleted_at"))
@Data
public class PolicyPlan {
    @Id @UuidGenerator
    private UUID id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "duration_months", nullable = false)
    private int durationMonths;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(name = "gst_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstPercentage;
    @Column(name = "terms_and_conditions", columnDefinition = "text")
    private String termsAndConditions;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecordStatus status = RecordStatus.ACTIVE;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

}
