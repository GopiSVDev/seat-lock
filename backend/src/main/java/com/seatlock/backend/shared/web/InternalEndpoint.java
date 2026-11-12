package com.seatlock.backend.shared.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Service-to-service controller: not under /api/v1 and not routed by the gateway
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InternalEndpoint {
}
