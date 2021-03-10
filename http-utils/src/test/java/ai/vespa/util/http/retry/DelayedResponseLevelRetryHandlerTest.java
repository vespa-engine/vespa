package ai.vespa.util.http.retry;// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author bjorncs
 */
public class DelayedResponseLevelRetryHandlerTest {

    @Test
    @SuppressWarnings("unchecked")
    public void retry_consumers_are_invoked() {
        RetryConsumer<HttpResponse> retryConsumer = mock(RetryConsumer.class);
        RetryFailedConsumer<HttpResponse> retryFailedConsumer = mock(RetryFailedConsumer.class);

        Duration delay = Duration.ofSeconds(10);
        int maxRetries = 5;

        DelayedResponseLevelRetryHandler handler = DelayedResponseLevelRetryHandler.Builder
                .withFixedDelay(delay, maxRetries)
                .onRetry(retryConsumer)
                .onRetryFailed(retryFailedConsumer)
                .build();

        HttpResponse response = createResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
        HttpClientContext ctx = new HttpClientContext();
        int lastExecutionCount = maxRetries + 1;
        for (int i = 1; i <= lastExecutionCount; i++) {
            handler.retryRequest(response, i, ctx);
        }

        verify(retryFailedConsumer).onRetryFailed(response, lastExecutionCount, ctx);
        for (int i = 1; i < lastExecutionCount; i++) {
            verify(retryConsumer).onRetry(response, delay, i, ctx);
        }
    }

    @Test
    public void retry_with_fixed_delay_sleeps_for_expected_duration() {
        Duration delay = Duration.ofSeconds(2);
        int maxRetries = 2;

        DelayedResponseLevelRetryHandler handler = DelayedResponseLevelRetryHandler.Builder
                .withFixedDelay(delay, maxRetries)
                .build();

        HttpResponse response = createResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
        HttpClientContext ctx = new HttpClientContext();
        int lastExecutionCount = maxRetries + 1;
        for (int i = 1; i <= lastExecutionCount; i++) {
            handler.retryRequest(response, i, ctx);
            assertEquals(delay.toMillis(), handler.getRetryInterval());
        }
    }

    @Test
    public void retry_with_fixed_backoff_sleeps_for_expected_durations() {
        Duration startDelay = Duration.ofMillis(500);
        Duration maxDelay = Duration.ofSeconds(5);
        int maxRetries = 10;

        DelayedResponseLevelRetryHandler handler = DelayedResponseLevelRetryHandler.Builder
                .withExponentialBackoff(startDelay, maxDelay, maxRetries)
                .build();

        HttpResponse response = createResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
        HttpClientContext ctx = new HttpClientContext();
        int lastExecutionCount = maxRetries + 1;
        List<Duration> expectedIntervals =
                Arrays.asList(
                        startDelay, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4),
                        Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5),
                        Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5));
        for (int i = 1; i <= lastExecutionCount; i++) {
            handler.retryRequest(response, i, ctx);
            assertEquals(expectedIntervals.get(i-1).toMillis(), handler.getRetryInterval());
        }
    }

    @Test
    public void retries_for_listed_exceptions_until_max_retries_exceeded() {
        int maxRetries = 2;

        DelayedResponseLevelRetryHandler handler = DelayedResponseLevelRetryHandler.Builder
                .withFixedDelay(Duration.ofSeconds(2), maxRetries)
                .retryForStatusCodes(Arrays.asList(HttpStatus.SC_SERVICE_UNAVAILABLE, HttpStatus.SC_BAD_GATEWAY))
                .build();

        HttpResponse response = createResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
        HttpClientContext ctx = new HttpClientContext();
        int lastExecutionCount = maxRetries + 1;
        for (int i = 1; i < lastExecutionCount; i++) {
            assertTrue(handler.retryRequest(response, i, ctx));
        }
        assertFalse(handler.retryRequest(response, lastExecutionCount, ctx));
    }

    @Test
    public void does_not_retry_for_non_listed_exception() {
        DelayedResponseLevelRetryHandler handler = DelayedResponseLevelRetryHandler.Builder
                .withFixedDelay(Duration.ofSeconds(2), 2)
                .retryForStatusCodes(Arrays.asList(HttpStatus.SC_SERVICE_UNAVAILABLE, HttpStatus.SC_BAD_GATEWAY))
                .build();

        HttpResponse response = createResponse(HttpStatus.SC_OK);
        HttpClientContext ctx = new HttpClientContext();
        assertFalse(handler.retryRequest(response, 1, ctx));
    }

    private static HttpResponse createResponse(int statusCode) {
        return new BasicHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, statusCode, "reason phrase"));
    }

}