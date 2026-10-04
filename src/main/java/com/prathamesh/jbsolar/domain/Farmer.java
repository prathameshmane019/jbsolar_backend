package com.prathamesh.jbsolar.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;
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

@Entity
@Table(name = "farmers", indexes = @Index(name = "farmers_deleted_at_index", columnList = "deleted_at"))
@Data
public class Farmer {
    @Id @UuidGenerator
    private UUID id;
    @Column(name = "customer_code", nullable = false, unique = true, length = 50)
    private String customerCode;
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Column(nullable = false, length = 20)
    private String mobile;
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
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
//    public UUID getId() { return id; }
//    public String getCustomerCode() { return customerCode; }
//    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
//    public String getFullName() { return fullName; }
//    public void setFullName(String fullName) { this.fullName = fullName; }
//    public String getMobile() { return mobile; }
//    public void setMobile(String mobile) { this.mobile = mobile; }
//    public String getAddress() { return address; }
//    public void setAddress(String address) { this.address = address; }
//    public String getDistrict() { return district; }
//    public void setDistrict(String district) { this.district = district; }
//    public String getTaluka() { return taluka; }
//    public void setTaluka(String taluka) { this.taluka = taluka; }
//    public String getVillage() { return village; }
//    public void setVillage(String village) { this.village = village; }
//    public VendorAgent getCreatedBy() { return createdBy; }
//    public void setCreatedBy(VendorAgent createdBy) { this.createdBy = createdBy; }
//    public Instant getCreatedAt() { return createdAt; }
}
