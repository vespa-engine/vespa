// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.text.Text;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.Trailers;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads request content from a Jetty {@link Request} and writes it to the {@link ContentChannel}
 * returned by the {@link com.yahoo.jdisc.handler.RequestHandler}.
 *
 * @author bjorncs
 */
class JettyRequestContentReader {

    private static final Logger log = Logger.getLogger(JettyRequestContentReader.class.getName());

    private final RequestMetricReporter metricReporter;
    private final Request jettyRequest;
    private final ContentChannel contentChannel;
    private final CompletableFuture<Void> jettyReadCompletion;
    private final CompletableFuture<Void> contentReadCompletion;
    // The initial value is decremented once the last chunk is processed
    private final AtomicLong numberOfOutstandingUserCalls = new AtomicLong(1);
    private final AtomicLong bytesRead = new AtomicLong();

    JettyRequestContentReader(RequestMetricReporter metricReporter, Janitor janitor, Request jettyRequest,
                              ContentChannel contentChannel) {
        this.metricReporter = Objects.requireNonNull(metricReporter);
        this.jettyRequest = Objects.requireNonNull(jettyRequest);
        var cfg = RequestUtils.getConnector(jettyRequest).connectorConfig();
        long maxContentSize = resolveMaxContentSize(cfg);
        var msgTemplate = resolveMaxContentSizeErrorMessage(cfg);
        this.contentChannel = maxContentSize >= 0
                ? new ByteLimitedContentChannel(
                Objects.requireNonNull(contentChannel), maxContentSize, msgTemplate, jettyRequest.getLength())
                : Objects.requireNonNull(contentChannel);
        Objects.requireNonNull(janitor);

        jettyReadCompletion = new CompletableFuture<Void>();
        contentReadCompletion = new CompletableFuture<Void>();

        // Wire in final completion logic
        jettyReadCompletion
                .whenComplete((result, originalError) -> {
                    metricReporter.contentSize(bytesRead.get());
                    if (originalError != null) {
                        log.log(Level.FINE, originalError, () -> "Request content read failed");
                        // Propagate error right away to ensure failure response is produced
                        // before content channel has a chance to hide the failure
                        contentReadCompletion.completeExceptionally(originalError);
                    }

                    // Dispatch to separate thread to avoid invoking content channel using user thread
                    janitor.scheduleTask(() -> {
                        if (originalError != null) {
                            try {
                                contentChannel.onError(originalError);
                            } catch (Throwable throwable) {
                                log.log(Level.FINE, throwable, () -> "Failed to invoke content channel onError");
                                originalError.addSuppressed(throwable);
                            }
                        }
                        try {
                            contentChannel.close(new CompletionHandler() {
                                @Override
                                public void completed() {
                                    contentReadCompletion.complete(null);
                                }

                                @Override
                                public void failed(Throwable t) {
                                    if (originalError != null) {
                                        originalError.addSuppressed(t);
                                    } else {
                                        contentReadCompletion.completeExceptionally(t);
                                    }
                                }
                            });
                        } catch (Throwable throwable) {
                            log.log(Level.FINE, throwable, () -> "Failed to invoke content channel close");
                            if (originalError != null) {
                                originalError.addSuppressed(throwable);
                            } else {
                                if (!contentReadCompletion.completeExceptionally(throwable)) {
                                    HttpServerConformanceTestHooks.markAsProcessed(throwable);
                                }
                            }
                        }
                    });
                });
    }

    CompletableFuture<Void> readCompletion() { return contentReadCompletion; }

    void start() { processChunks(); }

    private void processChunks() {
        while (true) {
            var chunk = jettyRequest.read();
            if (chunk instanceof Trailers trailers) {
                log.log(Level.FINE, () -> "Received trailers: " + trailers);
                chunk.release();
                return;
            }
            // Retry read if failure but not the last chunk
            if (chunk == null || Content.Chunk.isFailure(chunk, false)) {
                log.log(Level.FINE, chunk.getFailure(), () -> "Failed to read non-last chunk");
                jettyRequest.demand(this::processChunks);
                return;
            }
            if (Content.Chunk.isFailure(chunk, true)) {
                log.log(Level.FINE, chunk.getFailure(), () -> "Failed to read last chunk");
                jettyReadCompletion.completeExceptionally(chunk.getFailure());
                return;
            }
            var bytesRemaining = chunk.remaining();
            if (bytesRemaining > 0) {
                bytesRead.addAndGet(bytesRemaining);
                // One for the write() and one for the completion
                numberOfOutstandingUserCalls.addAndGet(2);
                var chunkReleaser = new CompletableFuture<Void>()
                        .whenComplete((result, error) -> chunk.release());
                try {
                    contentChannel.write(chunk.getByteBuffer(), new CompletionHandler() {
                        @Override
                        public void completed() {
                            decrementOutstandingUserCalls();
                            chunkReleaser.complete(null);
                        }

                        @Override
                        public void failed(Throwable t) {
                            jettyReadCompletion.completeExceptionally(t);
                            // Decremented for brevity - completionFuture is already completed with a failure
                            decrementOutstandingUserCalls();
                            chunkReleaser.complete(null);
                            log.log(Level.FINE, t, () -> "Failed to write chunk to content channel");
                        }
                    });
                    metricReporter.successfulWrite(bytesRemaining);
                } catch (Throwable t) {
                    chunkReleaser.complete(null);
                    log.log(Level.FINE, t, () -> "Failed to invoke content channel write");
                    jettyReadCompletion.completeExceptionally(t);
                } finally {
                    decrementOutstandingUserCalls();
                }
            } else {
                chunk.release();
            }
            if (chunk.isLast()) {
                // The last chunk is observed, decrement the initial value
                decrementOutstandingUserCalls();
                return; // EOF
            }
        }
    }

    private void decrementOutstandingUserCalls() {
        var remaining = numberOfOutstandingUserCalls.decrementAndGet();
        if (remaining == 0) {
            jettyReadCompletion.complete(null);
        }
        if (remaining < 0) {
            throw new AssertionError("Number of outstanding user calls is negative: " + remaining);
        }
    }

    private static String resolveMaxContentSizeErrorMessage(ConnectorConfig cfg) {
        return cfg.maxContentSizeErrorMessageTemplate().strip();
    }

    private static long resolveMaxContentSize(ConnectorConfig cfg) {
        // Scale based on max heap size if 0
        long maxContentSize = cfg.maxContentSize() != 0
                ? cfg.maxContentSize() : Math.min(Runtime.getRuntime().maxMemory() / 2, Integer.MAX_VALUE - 8);
        log.fine(() -> Text.format("maxContentSize=%d", maxContentSize));
        return maxContentSize;
    }

    private class ByteLimitedContentChannel implements ContentChannel {
        private final long maxContentSize;
        private final String messageTemplate;
        private final long contentLengthHeader;
        private final AtomicLong bytesWritten = new AtomicLong();
        private final ContentChannel delegate;

        ByteLimitedContentChannel(ContentChannel delegate, long maxContentSize, String messageTemplate, long contentLengthHeader) {
            this.delegate = delegate;
            this.maxContentSize = maxContentSize;
            this.messageTemplate = messageTemplate;
            this.contentLengthHeader = contentLengthHeader;
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            long written = bytesWritten.addAndGet(buf.remaining());
            if (contentLengthHeader != -1 && contentLengthHeader > maxContentSize) {
                handler.failed(new RequestException(
                        HttpStatus.PAYLOAD_TOO_LARGE_413, messageTemplate.formatted(contentLengthHeader, maxContentSize)));
            } else if (written > maxContentSize) {
                handler.failed(new RequestException(
                        HttpStatus.PAYLOAD_TOO_LARGE_413, messageTemplate.formatted(written, maxContentSize)));
            } else {
                delegate.write(buf, handler);
            }
        }

        @Override public void close(CompletionHandler h) { delegate.close(h); }
        @Override public void onError(Throwable t) { delegate.onError(t); }
    }
}
