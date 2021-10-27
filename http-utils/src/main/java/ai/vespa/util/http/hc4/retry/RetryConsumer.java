// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc4.retry;

import org.apache.http.client.protocol.HttpClientContext;

import java.time.Duration;

/**
 * Invoked before performing a delay and retry.
 *
 * @author bjorncs
 */
@FunctionalInterface
public interface RetryConsumer<T> {
    void onRetry(T data, Duration delay, int executionCount, HttpClientContext context);
}
