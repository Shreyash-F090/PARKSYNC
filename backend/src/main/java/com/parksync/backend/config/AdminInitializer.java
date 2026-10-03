package com.parksync.backend.config;

import com.parksync.backend.model.AppUser;
import com.parksync.backend.model.Role;
import com.parksync.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminInitializer {
    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    @Bean
    CommandLineRunner initializeAdmin(UserRepository users, PasswordEncoder encoder,
                                      @Value("${parksync.admin.email:}") String email,
                                      @Value("${parksync.admin.password:}") String password,
                                      @Value("${parksync.admin.name:PARKSYNC Administrator}") String name) {
        return args -> {
            if (email.isBlank() && password.isBlank()) {
                log.warn("No initial administrator configured. Set ADMIN_EMAIL and ADMIN_PASSWORD to create one.");
                return;
            }
            if (email.isBlank() || password.length() < 14) {
                throw new IllegalStateException("Set both ADMIN_EMAIL and an ADMIN_PASSWORD of at least 14 characters.");
            }
            String normalizedEmail = email.trim().toLowerCase();
            if (users.existsByEmailIgnoreCase(normalizedEmail)) return;
            AppUser admin = new AppUser();
            admin.setName(name);
            admin.setEmail(normalizedEmail);
            admin.setPhone("Not provided");
            admin.setRole(Role.ADMIN);
            admin.setPasswordHash(encoder.encode(password));
            users.save(admin);
            log.info("Initial administrator account created for configured ADMIN_EMAIL.");
        };
    }
}