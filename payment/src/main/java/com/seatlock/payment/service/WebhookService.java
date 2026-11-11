package com.seatlock.payment.service;

import com.seatlock.payment.booking.BookingClient;
import com.seatlock.payment.domain.AttemptStatus;
import com.seatlock.payment.domain.Payment;
import com.seatlock.payment.razorpay.RazorpaySignature;
import com.seatlock.payment.repository.PaymentRepository;
import com.seatlock.payment.repository.PaymentWriteRepository;
import com.seatlock.payment.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

// Razorpay events (https://razorpay.com/docs/webhooks/). Razorpay retries anything that is not 2xx and may
// send an event twice, so every handler is idempotent and a failure is thrown, never swallowed.
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RazorpaySignature signature;
    private final JsonMapper jsonMapper;
    private final PaymentRepository paymentRepository;
    private final PaymentWriteRepository paymentWriteRepository;
    private final PaymentService paymentService;
    private final BookingClient bookingClient;

    public void handle(byte[] body, String signatureHeader) {
        if (!signature.validWebhook(body, signatureHeader)) {
            throw new BadRequestException("Invalid webhook signature");
        }
        JsonNode event = jsonMapper.readTree(body);
        JsonNode payload = event.path("payload");
        switch (text(event.path("event"))) {
            case "payment.captured" -> paymentCaptured(payload.path("payment").path("entity"));
            case "payment.failed" -> paymentFailed(payload.path("payment").path("entity"));
            case "refund.processed" -> refundProcessed(payload.path("refund").path("entity"));
            case null, default -> log.info("Ignoring Razorpay event {}", text(event.path("event")));
        }
    }

    // Also covers the buyer who paid and closed the browser before it could tell us
    private void paymentCaptured(JsonNode entity) {
        Payment payment = byOrder(text(entity.path("order_id")));
        if (payment == null) {
            return;
        }
        paymentService.markCaptured(payment, text(entity.path("id")), text(entity.path("method")));
        // Every time, even if already captured: an earlier delivery may have failed to
        // reach the backend.
        // If the backend fails now, the 5xx makes Razorpay deliver this event again.
        bookingClient.paid(payment.getBookingId(), payment.getId());
    }

    // A failed try does not end the order: the buyer can still pay it another way
    private void paymentFailed(JsonNode entity) {
        Payment payment = byOrder(text(entity.path("order_id")));
        if (payment != null) {
            paymentWriteRepository.recordAttempt(payment.getId(), text(entity.path("id")), AttemptStatus.FAILED,
                    text(entity.path("method")), text(entity.path("error_description")), now());
        }
    }

    private void refundProcessed(JsonNode entity) {
        String razorpayPaymentId = text(entity.path("payment_id"));
        paymentRepository.findByRazorpayPaymentId(razorpayPaymentId).ifPresentOrElse(
                payment -> paymentWriteRepository.completeRefund(payment.getId(), now()),
                () -> log.warn("Refund for unknown Razorpay payment {}", razorpayPaymentId));
    }

    // Orders made elsewhere on the same Razorpay account are not ours: acknowledge
    // and ignore
    private Payment byOrder(String razorpayOrderId) {
        Payment payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (payment == null) {
            log.warn("Event for unknown Razorpay order {}", razorpayOrderId);
        }
        return payment;
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
