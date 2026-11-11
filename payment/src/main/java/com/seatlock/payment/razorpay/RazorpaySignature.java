package com.seatlock.payment.razorpay;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

// Only Razorpay and we know the secrets, so a matching HMAC proves the message came from Razorpay
// and was not changed (e.g. a forged "captured" or someone else's payment id).
@Component
@RequiredArgsConstructor
public class RazorpaySignature {

    private final RazorpayProperties properties;

    // What Checkout hands the browser after a successful payment
    public boolean validPayment(String razorpayOrderId, String razorpayPaymentId, String signature) {
        byte[] payload = (razorpayOrderId + "|" + razorpayPaymentId).getBytes(StandardCharsets.UTF_8);
        return matches(payload, properties.keySecret(), signature);
    }

    // Signed over the raw body: re-serialised JSON would not match byte for byte
    public boolean validWebhook(byte[] body, String signature) {
        return matches(body, properties.webhookSecret(), signature);
    }

    public static String hmacHex(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matches(byte[] payload, String secret, String signature) {
        if (signature == null) {
            return false;
        }
        byte[] expected = hmacHex(payload, secret).getBytes(StandardCharsets.UTF_8);
        // Constant time, so response timing can't reveal how much of a guess was right
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8));
    }
}
