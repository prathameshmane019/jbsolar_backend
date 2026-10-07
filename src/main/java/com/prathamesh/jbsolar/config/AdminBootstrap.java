package com.prathamesh.jbsolar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.prathamesh.jbsolar.domain.User;
import com.prathamesh.jbsolar.domain.UserRole;
import com.prathamesh.jbsolar.repository.UserRepository;

@Configuration
public class AdminBootstrap {
    @Bean
    ApplicationRunner createInitialAdmin(UserRepository users, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.mobile:}") String mobile,
            @Value("${app.bootstrap-admin.password:}") String password) {
        return args -> {
            if (mobile.isBlank() != password.isBlank()) {
                throw new IllegalStateException("Set both APP_ADMIN_MOBILE and APP_ADMIN_PASSWORD to bootstrap an admin");
            }
            if (!mobile.isBlank() && !users.existsByMobile(mobile)) {
                User admin = new User();
                admin.setMobile(mobile);
                admin.setPasswordHash(encoder.encode(password));
                admin.setRole(UserRole.ADMIN);
                users.save(admin);
            }
        };
    }
}
