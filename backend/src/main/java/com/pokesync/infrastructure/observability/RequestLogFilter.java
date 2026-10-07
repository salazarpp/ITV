package com.pokesync.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-ID";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String requestId = supplied != null && SAFE_ID.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        String previous = MDC.get("requestId");
        MDC.put("requestId", requestId);
        request.setAttribute("requestId", requestId);
        response.setHeader(HEADER, requestId);
        long started = System.nanoTime();
        boolean failed = false;
        // Query strings, request bodies, usernames and Authorization headers are never logged.
        String path = safePath(request.getRequestURI());
        log.info("POKESYNC-HTTP-0001 | start method={} path={}", request.getMethod(), path);
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            log.error("POKESYNC-HTTP-ERR-500 | filter failure method={} path={} diagnostic={}",
                    request.getMethod(), path, SafeExceptionDiagnostics.describe(exception));
            throw exception;
        } finally {
            int status = failed ? 500 : response.getStatus();
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            if (status >= 500) {
                log.error("POKESYNC-HTTP-0002 | complete method={} path={} status={} durationMs={}",
                        request.getMethod(), path, status, durationMs);
            } else if (status >= 400) {
                log.warn("POKESYNC-HTTP-0002 | complete method={} path={} status={} durationMs={}",
                        request.getMethod(), path, status, durationMs);
            } else {
                log.info("POKESYNC-HTTP-0002 | complete method={} path={} status={} durationMs={}",
                        request.getMethod(), path, status, durationMs);
            }
            if (previous == null) MDC.remove("requestId");
            else MDC.put("requestId", previous);
        }
    }

    private static String safePath(String path) {
        String clean = path.replaceAll("[\\r\\n\\t|]", "_");
        return clean.substring(0, Math.min(clean.length(), 256));
    }
}
