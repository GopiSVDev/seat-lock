package com.seatlock.payment.web;

import com.seatlock.payment.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

// Public (Razorpay has no token); the signature is the authentication
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    // Raw bytes: the signature covers the body exactly as sent
    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature) {
        webhookService.handle(body, signature);
        return ResponseEntity.ok().build();
    }
}
