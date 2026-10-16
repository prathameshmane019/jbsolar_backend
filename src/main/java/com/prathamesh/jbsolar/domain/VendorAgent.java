package com.prathamesh.jbsolar.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "vendor_agents",
        indexes = {
                @Index(
                        name = "vendor_agents_deleted_at_index",
                        columnList = "deleted_at"
                ),
                @Index(
                        name = "vendor_agents_vendor_id_index",
                        columnList = "vendor_id"
                )
        }
)
@Data
public class VendorAgent {

    @Id
    @UuidGenerator
    @Column(
            nullable = false,
            updatable = false
    )
    private UUID id;

    @Column(
            name = "agent_code",
            nullable = false,
            unique = true,
            length = 30,
            insertable = false,
            updatable = false
    )
    private String agentCode;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true
    )
    private User user;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "vendor_id",
            nullable = false
    )
    private Vendor vendor;

    @Column(
            name = "full_name",
            nullable = false,
            length = 150
    )
    private String fullName;

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