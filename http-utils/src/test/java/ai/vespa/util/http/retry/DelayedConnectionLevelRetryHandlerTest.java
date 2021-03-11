// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.retry;

import org.apache.http.client.protocol.HttpClientContext;
import org.junit.Test;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author bjorncs
 */
public class DelayedConnectionLevelRetryHandlerTest {

    @SuppressWarnings("unchecked")
    @Test
    public void retry_consumers_are_invoked() {
        RetryConsumer<IOException> retryConsumer = (RetryConsumer<IOException>) mock(RetryConsumer.class);
        RetryFailedConsumer<IOException> retryFailedConsumer = (RetryFailedConsumer<IOException>) mock(RetryFailedConsumer.class);

        Duration delay = Duration.ofSeconds(10);
        int maxRetries = 5;

        DelayedConnectionLevelRetryHandler handler = DelayedConnectionLevelRetryHandler.Builder
                .withFixedDelay(delay, maxRetries)
                .withSleeper(mock(Sleeper.class))
                .onRetry(retryConsumer)
                .onRetryFailed(retryFailedConsumer)
                .build();

        IOException exception = new IOException();
        HttpClientContext ctx = new HttpClientContext();
        int lastExecutionCount = maxRetries + 1;
        for (int i = 1; i <= lastExecutionCount; i++) {
            handler.retryRequest(exception, i, ctx);
        }

        verify(retryFailedConsumer).onRetryFailed(exception, lastExecutionCount, ctx);
        for (int i = 1; i < lastExecutionCount; i++) {
            verify(retryConsumer).onRetry(exception, delay, i, ctx);
        }
    }

    @Test
    public void retry_with_fixed_delay_sleeps_for_expected_duration() {
        Sleeper sleeper = mock(Sleeper.class);

        Duration delay = Duration.ofSeconds(2);
        int maxRetries = 2;

        DelayedConnectionLevelRetryHandler handler = DelayedConnectionLevelRetryHandler.Builder
                .withFixedDelay(delay, maxRetries)
                .withSleeper(sleeper)
                .build();

        IOException exception = new IOException();
        HttpClientContext ctx = new HttpClientContext();
        int lastExecutionCount = maxRetries + 1;
        for (int i = 1; i <= lastExecutionCount; i++) {
            handler.retryRequest(exception, i, ctx);
        }

        verify(sleeper, times(2)).sleep(delay);
    }

    @Test
    public void retry_with_fixed_backoff_sleeps_for_expected_durations() {
        Sleeper sleeper = mock(Sleeper.class);

        Duration startDelay = Duration.ofMillis(500);
        Duration maxDelay = Duration.ofSeconds(5);
        int maxRetries = 10;

        DelayedConnectionLevelRetryHandler handler = DelayedConnectionLevelRetryHandler.Builder
                .withExponentialBackoff(startDelay, maxDelay, maxRetries)
                .withSleeper(sleeper)
                .build();

        IOException exception = new IOException();
        HttpClientContext ctx = new HttpClientContext();
        int lastExecutionCount = maxRetries + 1;
        for (int i = 1; i <= lastExecutionCount; i++) {
            handler.retryRequest(exception, i, ctx);
        }

        verify(sleeper).sleep(startDelay);
        verify(sleeper).sleep(Duration.ofSeconds(1));
        verify(sleeper).sleep(Duration.ofSeconds(2));
        verify(sleeper).sleep(Duration.ofSeconds(4));
        verify(sleeper, times(6)).sleep(Duration.ofSeconds(5));
    }

    @Test
    public void retries_for_listed_exceptions_until_max_retries_exceeded() {
        int maxRetries = 2;

        DelayedConnectionLevelRetryHandler handler = DelayedConnectionLevelRetryHandler.Builder
                .withFixedDelay(Duration.ofSeconds(2), maxRetries)
                .retryForExceptions(Arrays.asList(SSLException.class, ConnectException.class))
                .withSleeper(mock(Sleeper.class))
                .build();

        SSLException sslException = new SSLException("ssl error");
        HttpClientContext ctx = new HttpClientContext();
        int lastExecutionCount = maxRetries + 1;
        for (int i = 1; i < lastExecutionCount; i++) {
            assertTrue(handler.retryRequest(sslException, i, ctx));
        }
        assertFalse(handler.retryRequest(sslException, lastExecutionCount, ctx));
    }

    @Test
    public void does_not_retry_for_non_listed_exception() {
        DelayedConnectionLevelRetryHandler handler = DelayedConnectionLevelRetryHandler.Builder
                .withFixedDelay(Duration.ofSeconds(2), 2)
                .retryForExceptions(Arrays.asList(SSLException.class, ConnectException.class))
                .withSleeper(mock(Sleeper.class))
                .build();

        IOException ioException = new IOException();
        HttpClientContext ctx = new HttpClientContext();
        assertFalse(handler.retryRequest(ioException, 1, ctx));
    }

}