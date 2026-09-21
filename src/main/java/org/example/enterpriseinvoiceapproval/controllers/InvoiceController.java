package org.example.enterpriseinvoiceapproval.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.enterpriseinvoiceapproval.modules.workflow.InvoiceEntity;
import org.example.enterpriseinvoiceapproval.modules.workflow.InvoiceService;
import org.example.enterpriseinvoiceapproval.modules.workflow.dto.DecisionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InvoiceEntity> uploadInvoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam("vendorName") String vendorName,
            @RequestParam("amount") BigDecimal amount,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String email = jwt.getClaimAsString("email");
        String fullName = jwt.getClaimAsString("name");

        InvoiceEntity invoice = invoiceService.createInvoice(file, vendorName, amount, email, fullName);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }

    @PutMapping("/{id}/decision")
    public ResponseEntity<InvoiceEntity> makeDecision(
            @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest decisionRequest,
            @AuthenticationPrincipal Jwt jwt
    ) {
        InvoiceEntity updatedInvoice = invoiceService.processDecision(
                id,
                decisionRequest,
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name")
        );
        return ResponseEntity.ok(updatedInvoice);
    }
}
