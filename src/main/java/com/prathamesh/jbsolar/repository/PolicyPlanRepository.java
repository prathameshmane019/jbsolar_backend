package com.prathamesh.jbsolar.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.prathamesh.jbsolar.domain.PolicyPlan;
import com.prathamesh.jbsolar.domain.RecordStatus;

public interface PolicyPlanRepository extends JpaRepository<PolicyPlan, UUID>, JpaSpecificationExecutor<PolicyPlan> {
    List<PolicyPlan> findAllByStatus(RecordStatus status);
}
