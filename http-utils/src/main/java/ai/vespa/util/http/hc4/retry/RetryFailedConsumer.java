// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc4.retry;

import org.apache.http.client.protocol.HttpClientContext;

/**
 * Invoked after the last retry has failed.
 *
 * @author bjorncs
 */
@FunctionalInterface
public interface RetryFailedConsumer<T> {
    void onRetryFailed(T response, int executionCount, HttpClientContext context);
}
