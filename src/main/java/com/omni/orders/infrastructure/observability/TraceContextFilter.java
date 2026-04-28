package com.omni.orders.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Ensures {@code requestId} is always present in MDC for every HTTP request.
 * Micrometer Brave populates {@code traceId} and {@code spanId} automatically.
 * The {@code requestId} is also injected into {@link OrderEventMessage} by
 * {@link com.omni.orders.infrastructure.kafka.producer.OrderEventMessageBuilder}
 * enabling end-to-end correlation from HTTP → outbox relay → Kafka consumer.
 */
@Component
@Order(1)
public class TraceContextFilter extends OncePerRequestFilter {

    private static final String HEADER  = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String h = request.getHeader(HEADER);
        return (h != null && !h.isBlank()) ? h : UUID.randomUUID().toString();
    }
}
