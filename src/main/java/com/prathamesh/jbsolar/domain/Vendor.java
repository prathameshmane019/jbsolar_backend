package com.prathamesh.jbsolar.domain;

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
@Table(name = "vendors", indexes = @Index(name = "vendors_deleted_at_index", columnList = "deleted_at"))
@Data
public class Vendor {
    @Id @UuidGenerator
    private UUID id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(name = "contact_person", length = 100)
    private String contactPerson;
    @Column(length = 20)
    private String mobile;
    @Column(length = 150)
    private String email;
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
