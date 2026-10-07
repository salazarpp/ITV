package com.pokesync.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pokesync.presentation.exception.ApiError;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLogFilterTest {
    @Test
    void propagatesSafeIdToHeaderAndErrorAndRestoresMdc() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/pokemon");
        request.addHeader("X-Request-ID", "client-trace_123");
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "previous");
        try {
            new RequestLogFilter().doFilter(request, response, (req, res) -> {
                assertThat(ApiError.of(404, "NOT_FOUND", "Missing").requestId()).isEqualTo("client-trace_123");
            });
            assertThat(response.getHeader("X-Request-ID")).isEqualTo("client-trace_123");
            assertThat(MDC.get("requestId")).isEqualTo("previous");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void replacesUnsafeIdAndClearsMdcAfterFailure() {
        var request = new MockHttpServletRequest("GET", "/api/v1/pokemon");
        request.addHeader("X-Request-ID", "injected\nlog|line");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new RequestLogFilter().doFilter(request, response, (req, res) -> {
            throw new IOException("sensitive-value");
        })).isInstanceOf(IOException.class);
        assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9-]{36}");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void logsStatusDurationAndCorrelationWithoutBodyQueryOrAuthorization() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        var request = new MockHttpServletRequest("POST", "/auth/login");
        request.addHeader("Authorization", "Bearer confidential-token");
        request.addHeader("X-Request-ID", "trace-example");
        request.setQueryString("password=confidential-query");
        request.setContent("confidential-body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            new RequestLogFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(401);
            });
            String messages = capture.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(messages).contains("POKESYNC-HTTP-0001", "POKESYNC-HTTP-0002", "status=401", "durationMs=")
                    .doesNotContain("confidential");
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    @Test
    void diagnosticsContainCauseTypeAndLocationWithoutMessages() {
        var failure = new IllegalStateException("secret-password", new IOException("secret-token"));
        String diagnostic = SafeExceptionDiagnostics.describe(failure);
        assertThat(diagnostic).contains("IllegalStateException", "IOException", "RequestLogFilterTest")
                .doesNotContain("secret-password", "secret-token");
    }
}
