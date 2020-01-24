// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.retry;

import org.apache.http.annotation.Contract;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * A {@link HttpRequestRetryHandler} that supports delayed retries.
 *
 * @author bjorncs
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DelayedHttpRequestRetryHandler implements HttpRequestRetryHandler {

    private static final Logger log = Logger.getLogger(HttpRequestRetryHandler.class.getName());

    @FunctionalInterface
    public interface RetryConsumer {
        void onRetry(IOException exception, Duration delay, int executionCount, HttpClientContext context);
    }

    @FunctionalInterface
    public interface RetryFailedConsumer {
        void onRetryFailed(IOException exception, int executionCount, HttpClientContext context);
    }

    @FunctionalInterface
    public interface RetryPredicate extends BiPredicate<IOException, HttpClientContext> {}

    private final DelaySupplier delaySupplier;
    private final int maxRetries;
    private final RetryPredicate predicate;
    private final RetryConsumer retryConsumer;
    private final RetryFailedConsumer retryFailedConsumer;
    private final Sleeper sleeper;

    private DelayedHttpRequestRetryHandler(
            DelaySupplier delaySupplier,
            int maxRetries,
            RetryPredicate predicate,
            RetryConsumer retryConsumer,
            RetryFailedConsumer retryFailedConsumer,
            Sleeper sleeper) {
        this.delaySupplier = delaySupplier;
        this.maxRetries = maxRetries;
        this.predicate = predicate;
        this.retryConsumer = retryConsumer;
        this.retryFailedConsumer = retryFailedConsumer;
        this.sleeper = sleeper;
    }

    @Override
    public boolean retryRequest(IOException exception, int executionCount, HttpContext ctx) {
        log.fine(() -> String.format("retryRequest(exception='%s', executionCount='%d', ctx='%s'",
                                     exception.getClass().getName(), executionCount, ctx));
        HttpClientContext clientCtx = HttpClientContext.adapt(ctx);
        if (!predicate.test(exception, clientCtx)) {
            log.fine(() -> String.format("Not retrying for '%s'", ctx));
            return false;
        }
        if (executionCount > maxRetries) {
            log.fine(() -> String.format("Max retries exceeded for '%s'", ctx));
            retryFailedConsumer.onRetryFailed(exception, executionCount, clientCtx);
            return false;
        }
        Duration delay = delaySupplier.getDelay(executionCount);
        log.fine(() -> String.format("Retrying after %s for '%s'", delay, ctx));
        retryConsumer.onRetry(exception, delay, executionCount, clientCtx);
        sleeper.sleep(delay);
        return true;
    }

    public static class Builder {

        private final DelaySupplier delaySupplier;
        private final int maxRetries;
        private RetryPredicate predicate = (ioException, ctx) -> true;
        private RetryConsumer retryConsumer = (exception, delay, count, ctx) -> {};
        private RetryFailedConsumer retryFailedConsumer = (exception, count, ctx) -> {};
        private Sleeper sleeper = new DefaultSleeper();

        private Builder(DelaySupplier delaySupplier, int maxRetries) {
            this.delaySupplier = delaySupplier;
            this.maxRetries = maxRetries;
        }

        public static Builder withFixedDelay(Duration delay, int maxRetries) {
            return new Builder(executionCount -> delay, maxRetries);
        }

        public static Builder withExponentialBackoff(Duration startDelay, Duration maxDelay, int maxRetries) {
            return new Builder(
                    executionCount -> {
                        Duration nextDelay = startDelay;
                        for (int i = 1; i < executionCount; ++i) {
                            nextDelay = nextDelay.multipliedBy(2);
                        }
                        return maxDelay.compareTo(nextDelay) > 0 ? nextDelay : maxDelay;
                    },
                    maxRetries);
        }

        public Builder retryForExceptions(List<Class<? extends IOException>> exceptionTypes) {
            this.predicate = (ioException, ctx) -> exceptionTypes.stream().anyMatch(type -> type.isInstance(ioException));
            return this;
        }

        public Builder retryForExceptions(Predicate<IOException> predicate) {
            this.predicate = (ioException, ctx) -> predicate.test(ioException);
            return this;
        }

        public Builder retryFor(RetryPredicate predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder onRetry(RetryConsumer consumer) {
            this.retryConsumer = consumer;
            return this;
        }

        public Builder onRetryFailed(RetryFailedConsumer consumer) {
            this.retryFailedConsumer = consumer;
            return this;
        }

        // For unit testing
        Builder withSleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        public DelayedHttpRequestRetryHandler build() {
            return new DelayedHttpRequestRetryHandler(delaySupplier, maxRetries, predicate, retryConsumer, retryFailedConsumer, sleeper);
        }

        private static class DefaultSleeper implements Sleeper {
            @Override
            public void sleep(Duration duration) {
                try {
                    Thread.sleep(duration.toMillis());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // For unit testing
    interface Sleeper {
        void sleep(Duration duration);
    }

    @FunctionalInterface
    private interface DelaySupplier {
        Duration getDelay(int executionCount);
    }
}
