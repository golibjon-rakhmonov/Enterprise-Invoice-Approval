package org.example.enterpriseinvoiceapproval.modules.workflow.rules;

import lombok.extern.slf4j.Slf4j;
import org.example.enterpriseinvoiceapproval.common.InvoiceStatus;
import org.example.enterpriseinvoiceapproval.modules.workflow.InvoiceEntity;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Order(10)
@Slf4j
public class SmallAmountRule implements ApprovalRule {

    private static final BigDecimal LIMIT = new BigDecimal("500.00");

    @Override
    public boolean apply(InvoiceEntity invoice) {
        if (invoice.getAmount().compareTo(LIMIT) < 0) {
            log.info("Rule triggered: small amount (< 500). Auto-approving invoice from {}", invoice.getVendorName());

            invoice.setStatus(InvoiceStatus.APPROVED);
            invoice.setRejectionReason("Auto-approved by system (Amount < 500)");
            return true;
        }

        return false;
    }
}
