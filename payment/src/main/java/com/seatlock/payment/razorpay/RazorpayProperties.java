package com.seatlock.payment.razorpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("razorpay")
public record RazorpayProperties(String baseUrl, String keyId, String keySecret, String webhookSecret) {
}
