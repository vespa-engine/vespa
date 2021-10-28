// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc4.retry;

import org.apache.http.HttpResponse;
import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * A {@link ServiceUnavailableRetryStrategy} that supports delayed retries on any response types.
 *
 * @author bjorncs
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DelayedResponseLevelRetryHandler implements ServiceUnavailableRetryStrategy {

    private static final Logger log = Logger.getLogger(DelayedResponseLevelRetryHandler.class.getName());

    private final DelaySupplier delaySupplier;
    private final int maxRetries;
    private final RetryPredicate<HttpResponse> predicate;
    private final RetryConsumer<HttpResponse> retryConsumer;
    private final RetryFailedConsumer<HttpResponse> retryFailedConsumer;
    private final ThreadLocal<Long> retryInterval = ThreadLocal.withInitial(() -> 0L);

    private DelayedResponseLevelRetryHandler(
            DelaySupplier delaySupplier,
            int maxRetries,
            RetryPredicate<HttpResponse> predicate,
            RetryConsumer<HttpResponse> retryConsumer,
            RetryFailedConsumer<HttpResponse> retryFailedConsumer) {

        this.delaySupplier = delaySupplier;
        this.maxRetries = maxRetries;
        this.predicate = predicate;
        this.retryConsumer = retryConsumer;
        this.retryFailedConsumer = retryFailedConsumer;
    }

    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext ctx) {
        log.fine(() -> String.format("retryRequest(responseCode='%s', executionCount='%d', ctx='%s'",
                                     response.getStatusLine().getStatusCode(), executionCount, ctx));
        HttpClientContext clientCtx = HttpClientContext.adapt(ctx);
        if (!predicate.test(response, clientCtx)) {
            log.fine(() -> String.format("Not retrying for '%s'", ctx));
            return false;
        }
        if (executionCount > maxRetries) {
            log.fine(() -> String.format("Max retries exceeded for '%s'", ctx));
            retryFailedConsumer.onRetryFailed(response, executionCount, clientCtx);
            return false;
        }
        Duration delay = delaySupplier.getDelay(executionCount);
        log.fine(() -> String.format("Retrying after %s for '%s'", delay, ctx));
        retryInterval.set(delay.toMillis());
        retryConsumer.onRetry(response, delay, executionCount, clientCtx);
        return true;
    }

    @Override
    public long getRetryInterval() {
        // Calls to getRetryInterval are always guarded by a call to retryRequest (using the same thread).
        // A thread local allows this retry handler to be thread safe and support dynamic retry intervals
        return retryInterval.get();
    }

    public static class Builder {

        private final DelaySupplier delaySupplier;
        private final int maxRetries;
        private RetryPredicate<HttpResponse> predicate = (response, ctx) -> true;
        private RetryConsumer<HttpResponse> retryConsumer = (response, delay, count, ctx) -> {};
        private RetryFailedConsumer<HttpResponse> retryFailedConsumer = (response, count, ctx) -> {};

        private Builder(DelaySupplier delaySupplier, int maxRetries) {
            this.delaySupplier = delaySupplier;
            this.maxRetries = maxRetries;
        }

        public static Builder withFixedDelay(Duration delay, int maxRetries) {
            return new Builder(new DelaySupplier.Fixed(delay), maxRetries);
        }

        public static Builder withExponentialBackoff(Duration startDelay, Duration maxDelay, int maxRetries) {
            return new Builder(new DelaySupplier.Exponential(startDelay, maxDelay), maxRetries);
        }

        public Builder retryForStatusCodes(List<Integer> statusCodes) {
            this.predicate = (response, ctx) -> statusCodes.contains(response.getStatusLine().getStatusCode());
            return this;
        }

        public Builder retryForResponses(Predicate<HttpResponse> predicate) {
            this.predicate = (response, ctx) -> predicate.test(response);
            return this;
        }

        public Builder retryFor(RetryPredicate<HttpResponse> predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder onRetry(RetryConsumer<HttpResponse> consumer) {
            this.retryConsumer = consumer;
            return this;
        }

        public Builder onRetryFailed(RetryFailedConsumer<HttpResponse> consumer) {
            this.retryFailedConsumer = consumer;
            return this;
        }

        public DelayedResponseLevelRetryHandler build() {
            return new DelayedResponseLevelRetryHandler(delaySupplier, maxRetries, predicate, retryConsumer, retryFailedConsumer);
        }
    }
}
