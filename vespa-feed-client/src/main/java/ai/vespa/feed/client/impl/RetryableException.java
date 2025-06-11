// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

/**
 * Signals that the operation should be retried, e.g. due to a transient network error, without
 */
class RetryableException extends RuntimeException {
    RetryableException(Throwable cause) { super(cause); }
}
