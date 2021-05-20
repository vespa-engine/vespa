// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Preconditions;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 */
class ServletRequestReader implements ReadListener {

    private enum State {
        READING, ALL_DATA_READ, REQUEST_CONTENT_CLOSED
    }

    private static final Logger log = Logger.getLogger(ServletRequestReader.class.getName());

    private static final int BUFFER_SIZE_BYTES = 8 * 1024;

    private final Object monitor = new Object();

    private final ServletInputStream servletInputStream;
    private final ContentChannel requestContentChannel;

    private final Janitor janitor;
    private final RequestMetricReporter metricReporter;

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
    private State state = State.READING;

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
    final CompletableFuture<Void> finishedFuture = new CompletableFuture<>();

    public ServletRequestReader(
            ServletInputStream servletInputStream,
            ContentChannel requestContentChannel,
            Janitor janitor,
            RequestMetricReporter metricReporter) {

        Preconditions.checkNotNull(servletInputStream);
        Preconditions.checkNotNull(requestContentChannel);
        Preconditions.checkNotNull(janitor);
        Preconditions.checkNotNull(metricReporter);

        this.servletInputStream = servletInputStream;
        this.requestContentChannel = requestContentChannel;
        this.janitor = janitor;
        this.metricReporter = metricReporter;
    }

    @Override
    public void onDataAvailable() throws IOException {
        while (servletInputStream.isReady()) {
            final byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            int numBytesRead;

            synchronized (monitor) {
                numBytesRead = servletInputStream.read(buffer);
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
                requestContentChannel.write(ByteBuffer.wrap(buffer, 0, numBytesRead), writeCompletionHandler);
                metricReporter.successfulRead(numBytesRead);
            }
            catch (Throwable t) {
                finishedFuture.completeExceptionally(t);
            }
            finally {
                //decrease due to this method completing.
                decreaseOutstandingUserCallsAndCloseRequestContentChannelConditionally();
            }
        }
    }

    private void decreaseOutstandingUserCallsAndCloseRequestContentChannelConditionally() {
        boolean shouldCloseRequestContentChannel;

        synchronized (monitor) {
            assertStateNotEquals(state, State.REQUEST_CONTENT_CLOSED);


            numberOfOutstandingUserCalls -= 1;

            shouldCloseRequestContentChannel = numberOfOutstandingUserCalls == 0 &&
                                               (finishedFuture.isDone() || state == State.ALL_DATA_READ);

            if (shouldCloseRequestContentChannel) {
                state = State.REQUEST_CONTENT_CLOSED;
            }
        }

        if (shouldCloseRequestContentChannel) {
            janitor.scheduleTask(this::closeCompletionHandler_noThrow);
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

    @Override
    public void onAllDataRead() {
        doneReading();
    }

    private void doneReading() {
        final boolean shouldCloseRequestContentChannel;

        int bytesRead;
        synchronized (monitor) {
            if (state != State.READING) {
                return;
            }

            state = State.ALL_DATA_READ;

            shouldCloseRequestContentChannel = numberOfOutstandingUserCalls == 0;
            if (shouldCloseRequestContentChannel) {
                state = State.REQUEST_CONTENT_CLOSED;
            }
            bytesRead = this.bytesRead;
        }

        if (shouldCloseRequestContentChannel) {
           closeCompletionHandler_noThrow();
        }

        metricReporter.contentSize(bytesRead);
    }

    private void closeCompletionHandler_noThrow() {
        //Cannot complete finishedFuture directly in completed(), as any exceptions after this fact will be ignored.
        // E.g.
        // close(CompletionHandler completionHandler) {
        //    completionHandler.completed();
        //    throw new RuntimeException
        // }

        CompletableFuture<Void> completedCalledFuture = new CompletableFuture<>();

        CompletionHandler closeCompletionHandler = new CompletionHandler() {
            @Override
            public void completed() {
                completedCalledFuture.complete(null);
            }

            @Override
            public void failed(final Throwable t) {
                finishedFuture.completeExceptionally(t);
            }
        };

        try {
            requestContentChannel.close(closeCompletionHandler);
            //if close did not cause an exception,
            // is it safe to pipe the result of the completionHandlerInvokedFuture into finishedFuture
            completedCalledFuture.whenComplete(this::setFinishedFuture);
        } catch (final Throwable t) {
            finishedFuture.completeExceptionally(t);
        }
    }

    private void setFinishedFuture(Void result, Throwable throwable) {
        if (throwable != null) {
            finishedFuture.completeExceptionally(throwable);
        } else {
            finishedFuture.complete(null);
        }
    }

    @Override
    public void onError(final Throwable t) {
        finishedFuture.completeExceptionally(t);
        doneReading();
    }

    private final CompletionHandler writeCompletionHandler = new CompletionHandler() {
        @Override
        public void completed() {
            decreaseOutstandingUserCallsAndCloseRequestContentChannelConditionally();
        }

        @Override
        public void failed(final Throwable t) {
            finishedFuture.completeExceptionally(t);
            decreaseOutstandingUserCallsAndCloseRequestContentChannelConditionally();
        }
    };
}
