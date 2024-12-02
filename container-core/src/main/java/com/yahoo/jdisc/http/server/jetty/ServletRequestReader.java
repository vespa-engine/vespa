// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.text.Text;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.server.Request;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.Response.Status.REQUEST_TOO_LONG;

/**
 * Finished when either
 * 1) There was an error
 * 2) There is no more data AND the number of pending completion handler invocations is 0
 *
 * Stops reading when a failure has happened.
 *
 * The reason for not waiting for pending completions in error situations
 * is that if the error is reported through the finishedFuture,
 * error reporting might be async.
 * Since we have tests that first reports errors and then closes the response content,
 * it's important that errors are delivered synchronously.
 *
 * @author Tony Vaagenes
 * @author Oyvind Bakksjo
 * @author bjorncs
 */
class ServletRequestReader {


    private enum State {
        NOT_STARTED, READING, ALL_DATA_READ, REQUEST_CONTENT_CLOSED
    }

    private static final Logger log = Logger.getLogger(ServletRequestReader.class.getName());

    private static final int BUFFER_SIZE_BYTES = 8 * 1024;

    private final Object monitor = new Object();

    private final HttpServletRequest req;
    private final ContentChannel requestContentChannel;
    private final Janitor janitor;
    private final RequestMetricReporter metricReporter;

    private ServletInputStream in;
    private Throwable errorDuringRead;
    private int bytesRead;

    /**
     * Rules:
     * 1. If state != State.READING,  then numberOfOutstandingUserCalls must not increase
     * 2. The _first time_ (finishedFuture is completed OR all data is read) AND numberOfOutstandingUserCalls == 0,
     *    the request content channel should be closed
     * 3. finishedFuture must not be completed when holding the monitor
     * 4. completing finishedFuture with an exception must be done synchronously
     *    to prioritize failures being transported to the response.
     * 5. All completion handlers (both for write and complete) must not be
     *    called from a user (request handler) owned thread
     *    (i.e. when being called from user code, don't call back into user code.)
     */
    // GuardedBy("monitor")
    private State state = State.NOT_STARTED;

    /**
     * Number of calls that we're waiting for from user code.
     * There are two classes of such calls:
     * 1) calls to requestContentChannel.write that we're waiting for to complete
     * 2) completion handlers given to requestContentChannel.write that the user must call.
     *
     * As long as we're waiting for such calls, we're not allowed to:
     * - close the request content channel (currently only required by tests)
     * - complete the finished future non-exceptionally,
     *   since then we would not be able to report writeCompletionHandler.failed(exception) calls
     */
    // GuardedBy("monitor")
    private int numberOfOutstandingUserCalls = 0;

    /**
     * When this future completes there will be no more calls against the servlet input stream.
     * The framework is still allowed to invoke us though.
     *
     * The future might complete in the servlet framework thread, user thread or executor thread.
     *
     * All completions of finishedFuture, except those done when closing the request content channel,
     * must be followed by calls to either onAllDataRead or decreasePendingAndCloseRequestContentChannelConditionally.
     * Those two functions will ensure that the request content channel is closed at the right time.
     * If calls to those methods does not close the request content channel immediately,
     * there is some outstanding completion callback that will later come in and complete the request.
     */
    private final CompletableFuture<Void> finishedFuture = new CompletableFuture<>();

    ServletRequestReader(
            Request req,
            ContentChannel requestContentChannel,
            Janitor janitor,
            RequestMetricReporter metricReporter) {
        this.req = Objects.requireNonNull(req);
        var cfg = RequestUtils.getConnector(req).connectorConfig();
        long maxContentSize = resolveMaxContentSize(cfg);
        var msgTemplate = resolveMaxContentSizeErrorMessage(cfg);
        this.requestContentChannel = maxContentSize >= 0
                ? new ByteLimitedContentChannel(
                        Objects.requireNonNull(requestContentChannel), maxContentSize, msgTemplate, req.getContentLengthLong())
                : Objects.requireNonNull(requestContentChannel);
        this.janitor = Objects.requireNonNull(janitor);
        this.metricReporter = Objects.requireNonNull(metricReporter);
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

    /** Register read listener to start reading request data */
    void start() {
        try {
            ServletInputStream in;
            synchronized (monitor) {
                if (state != State.NOT_STARTED) throw new IllegalStateException("State=" + state);
                in = req.getInputStream(); // may throw
                this.in = in;
                state = State.READING;
            }
            // Not holding monitor in case listener is invoked from this thread
            in.setReadListener(new Listener()); // may throw
        } catch (Throwable t) {
            fail(t);
        }
    }

    CompletableFuture<Void> finishedFuture() { return finishedFuture; }

    private class Listener implements ReadListener {

        @Override
        public void onDataAvailable() throws IOException {
            ServletInputStream in;
            synchronized (monitor) { in = ServletRequestReader.this.in; }
            while (in.isReady()) {
                final byte[] buffer = new byte[BUFFER_SIZE_BYTES];
                int numBytesRead;

                synchronized (monitor) {
                    numBytesRead = in.read(buffer);
                    if (numBytesRead < 0) {
                        // End of stream; there should be no more data available, ever.
                        return;
                    }
                    if (state != State.READING) {
                        //We have a failure, so no point in giving the buffer to the user.
                        assert finishedFuture.isCompletedExceptionally();
                        return;
                    }
                    //wait for both
                    //  - requestContentChannel.write to finish
                    //  - the write completion handler to be called
                    numberOfOutstandingUserCalls += 2;
                    bytesRead += numBytesRead;
                }

                try {
                    requestContentChannel.write(ByteBuffer.wrap(buffer, 0, numBytesRead), new CompletionHandler() {
                        @Override
                        public void completed() {
                            decreaseOutstandingUserCallsAndCloseRequestContentChannelConditionally();
                        }
                        @Override
                        public void failed(final Throwable t) {
                            finishedFuture.completeExceptionally(t);
                            decreaseOutstandingUserCallsAndCloseRequestContentChannelConditionally();
                        }
                    });
                    metricReporter.successfulRead(numBytesRead);
                } catch (Throwable t) {
                    finishedFuture.completeExceptionally(t);
                } finally {
                    //decrease due to this method completing.
                    decreaseOutstandingUserCallsAndCloseRequestContentChannelConditionally();
                }
            }
        }

        @Override public void onError(final Throwable t) { fail(t); }
        @Override public void onAllDataRead() { doneReading(null); }
    }

    void fail(Throwable t) {
        doneReading(t);
        finishedFuture.completeExceptionally(t);
    }

    private void decreaseOutstandingUserCallsAndCloseRequestContentChannelConditionally() {
        boolean shouldCloseRequestContentChannel;
        synchronized (monitor) {
            assertStateNotEquals(state, State.REQUEST_CONTENT_CLOSED);
            numberOfOutstandingUserCalls -= 1;
            shouldCloseRequestContentChannel = numberOfOutstandingUserCalls == 0 && state == State.ALL_DATA_READ;
            if (shouldCloseRequestContentChannel) {
                state = State.REQUEST_CONTENT_CLOSED;
            }
        }
        if (shouldCloseRequestContentChannel) {
            janitor.scheduleTask(this::closeRequestContentChannel);
        }
    }

    private void assertStateNotEquals(State state, State notExpectedState) {
        if (state == notExpectedState) {
            AssertionError e = new AssertionError("State should not be " + notExpectedState);
            log.log(Level.WARNING,
                    "Assertion failed. " +
                            "numberOfOutstandingUserCalls = " + numberOfOutstandingUserCalls +
                            ", isDone = " + finishedFuture.isDone(),
                    e);
            throw e;
        }
    }

    private void doneReading(Throwable t) {
        boolean shouldCloseRequestContentChannel;
        int bytesRead;

        synchronized (monitor) {
            errorDuringRead = t;
            if (state == State.REQUEST_CONTENT_CLOSED) {
                return;
            }
            if (state == State.READING) {
                state = State.ALL_DATA_READ;
            }
            shouldCloseRequestContentChannel = numberOfOutstandingUserCalls == 0;
            if (shouldCloseRequestContentChannel) {
                state = State.REQUEST_CONTENT_CLOSED;
            }
            bytesRead = this.bytesRead;
        }

        if (shouldCloseRequestContentChannel) {
           closeRequestContentChannel();
        }
        metricReporter.contentSize(bytesRead);
    }

    private void closeRequestContentChannel() {
        Throwable readError;
        synchronized (monitor) {  readError = this.errorDuringRead;  }
        try {
            if (readError != null) requestContentChannel.onError(readError);
            //Cannot complete finishedFuture directly in completed(), as any exceptions after this fact will be ignored.
            // E.g.
            // close(CompletionHandler completionHandler) {
            //    completionHandler.completed();
            //    throw new RuntimeException
            // }
            CompletableFuture<Void> completedCalledFuture = new CompletableFuture<>();
            requestContentChannel.close(new CompletionHandler() {
                @Override public void completed() {  completedCalledFuture.complete(null); }
                @Override public void failed(Throwable t) { finishedFuture.completeExceptionally(t); }
            });
            // Propagate successful completion as close did not throw an exception
            completedCalledFuture.whenComplete((__, ___) -> finishedFuture.complete(null));
        } catch (Throwable t) {
            finishedFuture.completeExceptionally(t);
        }
    }

    private static class ByteLimitedContentChannel implements ContentChannel {
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
                        REQUEST_TOO_LONG, messageTemplate.formatted(contentLengthHeader, maxContentSize)));
            } else if (written > maxContentSize) {
                handler.failed(new RequestException(
                        REQUEST_TOO_LONG, messageTemplate.formatted(written, maxContentSize)));
            } else {
                delegate.write(buf, handler);
            }
        }

        @Override public void close(CompletionHandler h) { delegate.close(h); }
        @Override public void onError(Throwable t) { delegate.onError(t); }
    }

}
