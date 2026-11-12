package com.seatlock.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

// A service that is down or too slow is the gateway's 502 or 504, not a 500
@Slf4j
final class UpstreamErrors {

    private UpstreamErrors() {
    }

    static ServerResponse toResponse(Throwable error, ServerRequest request) {
        HttpStatus status = timedOut(error) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        log.error("{} {} failed upstream: {}", request.method(), request.path(), error.toString());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                status == HttpStatus.GATEWAY_TIMEOUT ? "Service took too long to answer" : "Service unavailable");
        return ServerResponse.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static boolean timedOut(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
