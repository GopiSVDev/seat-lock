package com.seatlock.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

// The client the gateway forwards with (picked up by its RestClient)
@Configuration
public class HttpClientConfig {

    @Bean
    ClientHttpRequestFactory gatewayClientHttpRequestFactory(@Value("${gateway.read-timeout}") Duration readTimeout) {
        // HTTP/1.1: the JDK client's default HTTP/2 tries an h2c upgrade on plain http, which servers handle
        // unevenly. Redirects are never followed: they go back to the browser (e.g. /swagger-ui.html).
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
