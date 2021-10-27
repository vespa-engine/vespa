// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc4.retry;

import org.apache.http.client.protocol.HttpClientContext;

import java.util.function.BiPredicate;

/**
 * A predicate that determines whether an operation should be retried.
 *
 * @author bjorncs
 */
public interface RetryPredicate<T> extends BiPredicate<T, HttpClientContext> {}
