package com.prathamesh.jbsolar.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.prathamesh.jbsolar.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
    boolean existsByMobileAndIdNot(String mobile, UUID id);
}
