package org.example.enterpriseinvoiceapproval.modules.workflow.rules;

import lombok.extern.slf4j.Slf4j;
import org.example.enterpriseinvoiceapproval.common.InvoiceStatus;
import org.example.enterpriseinvoiceapproval.modules.workflow.InvoiceEntity;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@Slf4j
public class BlackListRule implements ApprovalRule{


    @Override
    public boolean apply(InvoiceEntity invoice) {

        if(invoice.getVendorName().toLowerCase().contains("scam")) {
            log.info("Rule triggered: Blacklist. Rejecting invoice from {}", invoice.getVendorName());

            invoice.setStatus(InvoiceStatus.REJECTED);
            invoice.setRejectionReason("Vendor is on the blacklist");

            return true;

        }

        return false;
    }
}
