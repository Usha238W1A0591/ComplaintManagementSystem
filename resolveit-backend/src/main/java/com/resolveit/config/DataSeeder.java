package com.resolveit.config;

import com.resolveit.model.ComplaintStatus;
import com.resolveit.model.Role;
import com.resolveit.model.User;
import com.resolveit.repository.ComplaintStatusRepository;
import com.resolveit.repository.RoleRepository;
import com.resolveit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds essential master data (roles + complaint statuses) on startup so the
 * application is functional on a fresh database (e.g. the Render PostgreSQL
 * instance, which is created from Hibernate's ddl-auto=update rather than the
 * MySQL schema script).
 *
 * All operations are idempotent - existing rows are never overwritten, so this
 * is safe to run against a locally-initialized MySQL database too.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RoleRepository roleRepository;
    private final ComplaintStatusRepository complaintStatusRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin-email:}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String bootstrapAdminPassword;

    public DataSeeder(RoleRepository roleRepository,
                      ComplaintStatusRepository complaintStatusRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.complaintStatusRepository = complaintStatusRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedRoles();
        seedComplaintStatuses();
        seedBootstrapAdmin();
    }

    private void seedRoles() {
        for (String name : List.of("ROLE_USER", "ROLE_OFFICER", "ROLE_ADMIN")) {
            if (roleRepository.findByName(name).isEmpty()) {
                Role role = new Role();
                role.setName(name);
                roleRepository.save(role);
                log.info("Seeded role: {}", name);
            }
        }
    }

    private void seedComplaintStatuses() {
        String[][] statuses = {
                {"NEW", "New"},
                {"UNDER_REVIEW", "Under Review"},
                {"IN_PROGRESS", "In Progress"},
                {"RESOLVED", "Resolved"},
                {"ESCALATED", "Escalated"},
                {"CLOSED", "Closed"}
        };
        for (String[] s : statuses) {
            if (complaintStatusRepository.findByCode(s[0]).isEmpty()) {
                ComplaintStatus status = new ComplaintStatus();
                status.setCode(s[0]);
                status.setDisplay(s[1]);
                complaintStatusRepository.save(status);
                log.info("Seeded complaint status: {}", s[0]);
            }
        }
    }

    private void seedBootstrapAdmin() {
        if (isBlank(bootstrapAdminEmail) || isBlank(bootstrapAdminPassword)) {
            log.info("Bootstrap admin not configured. Set app.bootstrap.admin-email and "
                    + "app.bootstrap.admin-password to auto-create an admin on first start.");
            return;
        }
        if (!userRepository.findByRolesName("ROLE_ADMIN").isEmpty()) {
            log.info("An admin user already exists; skipping bootstrap admin creation.");
            return;
        }
        if (userRepository.findByEmail(bootstrapAdminEmail).isPresent()) {
            log.warn("Bootstrap admin email '{}' is already in use but has no ADMIN role; skipping.",
                    bootstrapAdminEmail);
            return;
        }

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not seeded"));
        Role officerRole = roleRepository.findByName("ROLE_OFFICER").orElse(null);
        Role userRole = roleRepository.findByName("ROLE_USER").orElse(null);

        User admin = new User();
        admin.setFullName("System Administrator");
        admin.setUsername("admin");
        admin.setEmail(bootstrapAdminEmail);
        admin.setPassword(passwordEncoder.encode(bootstrapAdminPassword));
        // The admin is granted all three roles so the frontend role-based routing
        // (which sends admins to the officer dashboard, guarded by ROLE_OFFICER)
        // resolves correctly, and the admin can access both officer and admin pages.
        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(adminRole);
        if (officerRole != null) adminRoles.add(officerRole);
        if (userRole != null) adminRoles.add(userRole);
        admin.setRoles(adminRoles);
        userRepository.save(admin);
        log.info("Bootstrap admin created with email: {}", bootstrapAdminEmail);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
