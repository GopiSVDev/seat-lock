package com.seatlock.payment.web;

import com.seatlock.payment.service.PaymentService;
import com.seatlock.payment.web.dto.CreatePaymentRequest;
import com.seatlock.payment.web.dto.PaymentResponse;
import com.seatlock.payment.web.dto.RefundRequest;
import com.seatlock.payment.web.dto.VerifyPaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Called by the backend only (role PAYMENTS_CLIENT), never by browsers
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 201 for a new payment, 200 when the booking already had one
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentService.Created created = paymentService.create(request);
        return ResponseEntity.status(created.isNew() ? HttpStatus.CREATED : HttpStatus.OK).body(created.payment());
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return paymentService.get(id);
    }

    @PostMapping("/{id}/verify")
    public PaymentResponse verify(@PathVariable Long id, @Valid @RequestBody VerifyPaymentRequest request) {
        return paymentService.verify(id, request);
    }

    @PostMapping("/{id}/refund")
    public PaymentResponse refund(@PathVariable Long id, @Valid @RequestBody RefundRequest request) {
        return paymentService.refund(id, request);
    }
}
