package org.example.enterpriseinvoiceapproval;

import org.example.enterpriseinvoiceapproval.common.InvoiceStatus;
import org.example.enterpriseinvoiceapproval.modules.workflow.InvoiceEntity;
import org.example.enterpriseinvoiceapproval.modules.workflow.InvoiceService;
import org.example.enterpriseinvoiceapproval.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class InvoiceServiceIT extends AbstractIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Test
    void shouldCreateInvoiceAndUploadFile() {
        // GIVEN
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-invoice.pdf",
                "application/pdf",
                "Hello World Content".getBytes(StandardCharsets.UTF_8));

        String userEmail = "test@example.com";
        String userFullName = "Test User";
        BigDecimal amount = new BigDecimal("150.00");

        // WHEN
        InvoiceEntity result = invoiceService.createInvoice(file, "Test Vendor", amount, userEmail, userFullName);

        // THEN
        assertThat(result.getId()).isNotNull();
        assertThat(result.getS3FileKey()).contains("test-invoice.pdf");

        // Rule engine: 150 < 500, so the invoice is auto-approved
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.APPROVED);

        assertThat(result.getRejectionReason()).contains("Auto-approved");

        // Persisted
        InvoiceEntity savedInDb = invoiceRepository.findById(result.getId()).orElseThrow();
        assertThat(savedInDb.getVendorName()).isEqualTo("Test Vendor");
    }
}