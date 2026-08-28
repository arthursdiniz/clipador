package com.clipador.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ConcurrentRequestLimitFilterTest {
    @Test
    void rejectsSecondUploadWhileTheConfiguredSlotIsOccupied() throws Exception {
        ConcurrentRequestLimitFilter filter = new ConcurrentRequestLimitFilter(
                new ApiLimitsProperties(4, 1), new SimpleMeterRegistry());
        MockHttpServletRequest first = uploadRequest();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        filter.doFilter(first, firstResponse, (request, response) ->
                filter.doFilter(uploadRequest(), secondResponse, (ignoredRequest, ignoredResponse) -> {
                    throw new AssertionError("The second upload must not reach the application");
                }));

        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("5");
        assertThat(secondResponse.getContentType()).startsWith("application/problem+json");
    }

    private MockHttpServletRequest uploadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/videos/upload");
        request.setRequestURI("/api/v1/videos/upload");
        return request;
    }
}
