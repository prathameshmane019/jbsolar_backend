package com.prathamesh.jbsolar.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "farmers",
        indexes = {
                @Index(
                        name = "farmers_deleted_at_index",
                        columnList = "deleted_at"
                ),
                @Index(
                        name = "farmers_aadhaar_hash_unique",
                        columnList = "aadhaar_hash",
                        unique = true
                ),
                @Index(
                        name = "farmers_mobile_index",
                        columnList = "mobile"
                ),
                @Index(
                        name = "farmers_created_by_index",
                        columnList = "created_by"
                ),
                @Index(
                        name = "farmers_created_by_deleted_at_index",
                        columnList = "created_by, deleted_at"
                )
        }
)
@Data
public class Farmer {

    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(
            name = "customer_code",
            nullable = false,
            unique = true,
            length = 50,
            insertable = false,
            updatable = false
    )
    private String customerCode;

    @Column(
            name = "full_name",
            nullable = false,
            length = 150
    )
    private String fullName;

    @Column(
            nullable = false,
            length = 20
    )
    private String mobile;

    @Column(
            name = "aadhaar_hash",
            length = 64
    )
    private String aadhaarHash;

    @Column(columnDefinition = "text")
    private String address;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String taluka;

    @Column(length = 100)
    private String village;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private VendorAgent createdBy;

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
    void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}