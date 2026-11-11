package com.seatlock.payment.repository;

import com.seatlock.payment.domain.AttemptStatus;
import com.seatlock.payment.domain.RefundReason;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

// Status changes are guarded updates: the browser, the webhook and Razorpay's retries can race,
// and only the first transition may win. Reads stay on JPA.
@Repository
@RequiredArgsConstructor
public class PaymentWriteRepository {

    private final JdbcClient jdbcClient;

    public boolean capture(long id, String razorpayPaymentId, Instant now) {
        return jdbcClient.sql("""
                update payment.payment
                set status = 'CAPTURED', razorpay_payment_id = :razorpayPaymentId, updated_at = :now
                where id = :id and status = 'CREATED'
                """).param("id", id).param("razorpayPaymentId", razorpayPaymentId).param("now", Timestamp.from(now))
                .update() == 1;
    }

    // Razorpay sends the same event more than once; the last word on an attempt wins
    public void recordAttempt(long paymentId, String razorpayPaymentId, AttemptStatus status, String method,
            String errorDescription, Instant now) {
        jdbcClient.sql("""
                insert into payment.payment_attempt
                    (payment_id, razorpay_payment_id, status, method, error_description, created_at)
                values (:paymentId, :razorpayPaymentId, :status, :method, :errorDescription, :now)
                on conflict (razorpay_payment_id)
                do update set status = excluded.status, error_description = excluded.error_description
                """).param("paymentId", paymentId).param("razorpayPaymentId", razorpayPaymentId)
                .param("status", status.name()).param("method", method).param("errorDescription", errorDescription)
                .param("now", Timestamp.from(now)).update();
    }

    public boolean startRefund(long id, Instant now) {
        return setStatus(id, "CAPTURED", "REFUND_PENDING", now);
    }

    // Razorpay refused the refund: back to CAPTURED so it can be asked again
    public void undoRefund(long id, Instant now) {
        setStatus(id, "REFUND_PENDING", "CAPTURED", now);
    }

    public void insertRefund(long paymentId, String razorpayRefundId, long amount, RefundReason reason, Instant now) {
        jdbcClient.sql("""
                insert into payment.refund (payment_id, razorpay_refund_id, amount, reason, created_at)
                values (:paymentId, :razorpayRefundId, :amount, :reason, :now)
                """).param("paymentId", paymentId).param("razorpayRefundId", razorpayRefundId).param("amount", amount)
                .param("reason", reason.name()).param("now", Timestamp.from(now)).update();
    }

    public boolean completeRefund(long id, Instant now) {
        boolean completed = setStatus(id, "REFUND_PENDING", "REFUNDED", now);
        if (completed) {
            jdbcClient.sql("update payment.refund set processed_at = :now where payment_id = :id").param("id", id)
                    .param("now", Timestamp.from(now)).update();
        }
        return completed;
    }

    private boolean setStatus(long id, String from, String to, Instant now) {
        return jdbcClient.sql("""
                update payment.payment set status = :to, updated_at = :now
                where id = :id and status = :from
                """).param("id", id).param("from", from).param("to", to).param("now", Timestamp.from(now))
                .update() == 1;
    }
}
