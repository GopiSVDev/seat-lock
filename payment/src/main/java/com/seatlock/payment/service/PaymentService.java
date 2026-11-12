package com.seatlock.payment.service;

import com.seatlock.payment.domain.AttemptStatus;
import com.seatlock.payment.domain.Payment;
import com.seatlock.payment.domain.PaymentStatus;
import com.seatlock.payment.razorpay.RazorpayClient;
import com.seatlock.payment.razorpay.RazorpayProperties;
import com.seatlock.payment.razorpay.RazorpaySignature;
import com.seatlock.payment.repository.PaymentRepository;
import com.seatlock.payment.repository.PaymentWriteRepository;
import com.seatlock.payment.shared.exception.BadRequestException;
import com.seatlock.payment.shared.exception.ConflictException;
import com.seatlock.payment.shared.exception.DownstreamException;
import com.seatlock.payment.shared.exception.NotFoundException;
import com.seatlock.payment.web.dto.CreatePaymentRequest;
import com.seatlock.payment.web.dto.PaymentResponse;
import com.seatlock.payment.web.dto.RefundRequest;
import com.seatlock.payment.web.dto.VerifyPaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

// No method here is @Transactional: none may hold a DB connection while Razorpay answers.
// Each write is a single statement (or a guarded update), safe on its own.
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    static final String CURRENCY = "INR";

    private final PaymentRepository paymentRepository;
    private final PaymentWriteRepository paymentWriteRepository;
    private final RazorpayClient razorpay;
    private final RazorpaySignature signature;
    private final RazorpayProperties razorpayProperties;

    public record Created(PaymentResponse payment, boolean isNew) {
    }

    // Idempotent per booking: a retry gets the same Razorpay order back
    public Created create(CreatePaymentRequest request) {
        var existing = paymentRepository.findByBookingId(request.bookingId());
        if (existing.isPresent()) {
            log.info("Booking {} already has payment {} (order {})", request.bookingId(), existing.get().getId(),
                    existing.get().getRazorpayOrderId());
            return new Created(sameRequestOrConflict(existing.get(), request), false);
        }

        String orderId = razorpay.createOrder(request.amount(), CURRENCY, request.bookingId());
        try {
            Payment payment = paymentRepository.saveAndFlush(
                    new Payment(request.bookingId(), request.customerId(), request.amount(), CURRENCY, orderId, now()));
            log.info("Payment {} created for booking {}: order {}, {} paise", payment.getId(), request.bookingId(),
                    orderId, request.amount());
            return new Created(response(payment), true);
        } catch (DataIntegrityViolationException e) {
            // A parallel retry saved first; its order is used. Ours stays unpaid at
            // Razorpay, which is harmless.
            Payment winner = paymentRepository.findByBookingId(request.bookingId()).orElseThrow(() -> e);
            log.info("Booking {}: a parallel request created payment {} first; order {} stays unused",
                    request.bookingId(), winner.getId(), orderId);
            return new Created(sameRequestOrConflict(winner, request), false);
        }
    }

    public PaymentResponse get(Long id) {
        return response(find(id));
    }

    public PaymentResponse verify(Long id, VerifyPaymentRequest request) {
        Payment payment = find(id);
        if (!payment.getRazorpayOrderId().equals(request.razorpayOrderId()) || !signature
                .validPayment(request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())) {
            log.warn("Payment {}: browser report with an invalid signature (order {}, payment {})", id,
                    request.razorpayOrderId(), request.razorpayPaymentId());
            throw new BadRequestException("Invalid payment signature");
        }
        markCaptured(payment, request.razorpayPaymentId(), null, "browser");
        return response(find(id));
    }

    // Browser and webhook both land here; whichever is first captures, the other changes nothing
    // reportedBy: "browser" or "webhook", for the logs
    public void markCaptured(Payment payment, String razorpayPaymentId, String method, String reportedBy) {
        Instant now = now();
        paymentWriteRepository.recordAttempt(payment.getId(), razorpayPaymentId, AttemptStatus.CAPTURED, method, null,
                now);
        if (paymentWriteRepository.capture(payment.getId(), razorpayPaymentId, now)) {
            log.info("Payment {} captured ({}, reported by {})", payment.getId(), razorpayPaymentId, reportedBy);
        } else {
            String capturedBy = find(payment.getId()).getRazorpayPaymentId();
            if (razorpayPaymentId.equals(capturedBy)) {
                log.info("Payment {} already captured; {} report changes nothing", payment.getId(), reportedBy);
            } else {
                // Paid twice for one order: needs a refund by hand (not automated yet)
                log.warn("Payment {} already captured by {}, ignoring second capture {}", payment.getId(), capturedBy,
                        razorpayPaymentId);
            }
        }
    }

    // Idempotent: asking again while it is pending or done returns the payment unchanged
    public PaymentResponse refund(Long id, RefundRequest request) {
        if (!paymentWriteRepository.startRefund(id, now())) {
            Payment current = find(id);
            if (current.getStatus() == PaymentStatus.REFUND_PENDING || current.getStatus() == PaymentStatus.REFUNDED) {
                log.info("Payment {} refund asked again; already {}", id, current.getStatus());
                return response(current);
            }
            throw new ConflictException("Payment is not captured");
        }

        Payment payment = find(id);
        try {
            String refundId = razorpay.refund(payment.getRazorpayPaymentId(), payment.getAmount(),
                    request.reason().name());
            paymentWriteRepository.insertRefund(id, refundId, payment.getAmount(), request.reason(), now());
            log.info("Payment {} refund {} requested ({}, {} paise)", id, refundId, request.reason(),
                    payment.getAmount());
        } catch (DownstreamException e) {
            log.warn("Payment {} refund request failed; back to CAPTURED", id);
            paymentWriteRepository.undoRefund(id, now());
            throw e;
        }
        // A crash between Razorpay's answer and the insert leaves REFUND_PENDING without a refund row;
        // the refund.processed webhook still completes it (matched by payment id)
        return response(find(id));
    }

    public Payment find(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new NotFoundException("Payment", id));
    }

    private PaymentResponse sameRequestOrConflict(Payment payment, CreatePaymentRequest request) {
        if (payment.getAmount() != request.amount() || !Objects.equals(payment.getCustomerId(), request.customerId())) {
            throw new ConflictException("Booking already has a payment with a different amount or customer");
        }
        return response(payment);
    }

    private PaymentResponse response(Payment payment) {
        return PaymentResponse.from(payment, razorpayProperties.keyId());
    }

    // Microseconds: what timestamptz stores, so responses match later reads
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
