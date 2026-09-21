package org.example.enterpriseinvoiceapproval.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.enterpriseinvoiceapproval.Identity.UserEntity;
import org.example.enterpriseinvoiceapproval.modules.workflow.InvoiceEntity;
import org.example.enterpriseinvoiceapproval.repository.InvoiceRepository;
import org.example.enterpriseinvoiceapproval.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Seed only an empty database, so restarts do not duplicate data
        if (userRepository.count() > 0) {
            log.info("Database already seeded. Skipping.");
            return;
        }

        log.info("Seeding initial data...");

        // Sample manager
        UserEntity manager = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("manager@corp.com")
                .fullName("Big Boss")
                .role(Role.MANAGER)
                .active(true)
                .build();

        userRepository.save(manager);
        log.info("Saved User: {}", manager.getId());

        // Sample invoice waiting for a decision
        InvoiceEntity invoice = InvoiceEntity.builder()
                .userId(manager.getId())
                .vendorName("JetBrains Inc")
                .amount(new BigDecimal("149.00"))
                .currency("EUR")
                .description("IntelliJ IDEA License")
                .s3FileKey("invoices/2026/idea_license.pdf")
                .status(InvoiceStatus.PENDING_MANAGER)
                .build();

        invoiceRepository.save(invoice);
        log.info("Saved Invoice: {}", invoice.getId());

        log.info("Data seeding completed successfully!");
    }
}