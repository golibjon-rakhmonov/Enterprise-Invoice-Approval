package org.example.enterpriseinvoiceapproval.modules.workflow;

import lombok.RequiredArgsConstructor;
import org.example.enterpriseinvoiceapproval.Identity.UserEntity;
import org.example.enterpriseinvoiceapproval.common.InvoiceStatus;
import org.example.enterpriseinvoiceapproval.common.Role;
import org.example.enterpriseinvoiceapproval.modules.storage.StorageService;
import org.example.enterpriseinvoiceapproval.modules.workflow.dto.DecisionRequest;
import org.example.enterpriseinvoiceapproval.modules.workflow.events.InvoiceStatusChangedEvent;
import org.example.enterpriseinvoiceapproval.modules.workflow.rules.ApprovalRule;
import org.example.enterpriseinvoiceapproval.repository.InvoiceRepository;
import org.example.enterpriseinvoiceapproval.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final StorageService storageService;
    private final List<ApprovalRule> approvalRules;
    private final UserRepository userRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InvoiceEntity createInvoice(MultipartFile file, String vendorName, BigDecimal amount, String userEmail, String userFullName) {

        UserEntity user = findOrCreateUser(userEmail, userFullName, Role.EMPLOYEE);

        String s3Key = storageService.uploadFile(file);

        InvoiceEntity invoice = InvoiceEntity.builder()
                .userId(user.getId())
                .vendorName(vendorName)
                .amount(amount)
                .s3FileKey(s3Key)
                .status(InvoiceStatus.PENDING_MANAGER)
                .build();

        // Rules run in @Order; the first rule that makes a decision stops the chain.
        for (ApprovalRule rule : approvalRules) {
            if (rule.apply(invoice)) {
                break;
            }
        }

        InvoiceEntity savedInvoice = invoiceRepository.save(invoice);

        // A rule decided automatically: audit it with no actor, i.e. the system.
        if (savedInvoice.getStatus() != InvoiceStatus.PENDING_MANAGER) {
            eventPublisher.publishEvent(new InvoiceStatusChangedEvent(
                    savedInvoice.getId(),
                    null,
                    InvoiceStatus.PENDING_MANAGER,
                    savedInvoice.getStatus()
            ));
        }

        return savedInvoice;
    }

    @Transactional
    public InvoiceEntity processDecision(UUID invoiceId, DecisionRequest decisionRequest, String deciderEmail, String deciderFullName) {

        InvoiceEntity invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NoSuchElementException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.PENDING_MANAGER) {
            throw new IllegalStateException("Invoice has already been processed. Current status: " + invoice.getStatus());
        }

        if (decisionRequest.getStatus() != InvoiceStatus.APPROVED && decisionRequest.getStatus() != InvoiceStatus.REJECTED) {
            throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
        }

        UserEntity decider = findOrCreateUser(deciderEmail, deciderFullName, Role.MANAGER);
        InvoiceStatus oldStatus = invoice.getStatus();

        invoice.setStatus(decisionRequest.getStatus());
        invoice.setRejectionReason(decisionRequest.getComment());

        InvoiceEntity savedInvoice = invoiceRepository.save(invoice);

        // The actor is the manager who made the decision, not the employee who submitted the invoice.
        eventPublisher.publishEvent(new InvoiceStatusChangedEvent(
                savedInvoice.getId(),
                decider.getId(),
                oldStatus,
                savedInvoice.getStatus()
        ));

        return savedInvoice;
    }

    private UserEntity findOrCreateUser(String email, String fullName, Role role) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(UserEntity.builder()
                        .email(email)
                        .fullName(fullName != null ? fullName : email)
                        .role(role)
                        .active(true)
                        .build()));
    }
}
