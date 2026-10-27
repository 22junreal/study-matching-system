package com.studymatching.common.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.slf4j.*;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        response.setHeader("X-Request-ID", requestId);
        try (var ignored = MDC.putCloseable("requestId", requestId)) {
            try { chain.doFilter(request, response); }
            finally {
                // Deliberately exclude query strings, credentials, headers and request/response bodies.
                log.info("http_request method={} path={} status={} durationMs={}", request.getMethod(),
                        request.getRequestURI(), response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            }
        }
    }
}
