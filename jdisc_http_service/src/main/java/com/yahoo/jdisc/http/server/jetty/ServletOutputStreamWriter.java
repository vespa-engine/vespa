// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.handler.CompletionHandler;

import javax.annotation.concurrent.GuardedBy;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author tonytv
 * @author bjorncs
 */
public class ServletOutputStreamWriter {
    /** Rules:
     * 1) Don't modify the output stream without isReady returning true (write/flush/close).
     *    Multiple modification calls without interleaving isReady calls are not allowed.
     * 2) If isReady returned false, no other calls should be made until the write listener is invoked.
     * 3) If the write listener sees isReady == false, it must not do any modifications before its next invocation.
     */


    private enum State {
        NOT_STARTED,
        WAITING_FOR_WRITE_POSSIBLE_CALLBACK,
        WAITING_FOR_FIRST_BUFFER,
        WAITING_FOR_SUBSEQUENT_BUFFER,
        WRITING_BUFFERS,
        FINISHED_OR_ERROR
    }

    private static final Logger log = Logger.getLogger(ServletOutputStreamWriter.class.getName());

    // If so, application code could fake a close by writing such a byte buffer.
    // The problem can be solved by filtering out zero-length byte buffers from application code.
    // Other ways to express this are also possible, e.g. with a 'closed' state checked when queue goes empty.
    private static final ByteBuffer CLOSE_STREAM_BUFFER = ByteBuffer.allocate(0);

    private final Object monitor = new Object();

    @GuardedBy("monitor")
    private State state = State.NOT_STARTED;

    @GuardedBy("state")
    private final ServletOutputStream outputStream;
    private final Executor executor;

    @GuardedBy("monitor")
    private final Deque<ResponseContentPart> responseContentQueue = new ArrayDeque<>();

    private final MetricReporter metricReporter;

    /**
     * When this future completes there will be no more calls against the servlet output stream or servlet response.
     * The framework is still allowed to invoke us though.
     *
     * The future might complete in the servlet framework thread, user thread or executor thread.
     */
    final CompletableFuture<Void> finishedFuture = new CompletableFuture<>();


    public ServletOutputStreamWriter(ServletOutputStream outputStream, Executor executor, MetricReporter metricReporter) {
        this.outputStream = outputStream;
        this.executor = executor;
        this.metricReporter = metricReporter;
    }

    public void registerWriteListener() {
        outputStream.setWriteListener(writeListener);
    }

    public void sendErrorContentAndCloseAsync(ByteBuffer errorContent) {
        synchronized (monitor) {
            // Assert that no content has been written as it is too late to write error response if the response is committed.
            switch (state) {
                case NOT_STARTED:
                case WAITING_FOR_FIRST_BUFFER:
                    writeBuffer(errorContent, null);
                    close(null);
                    return;
                default:
                    throw createAndLogAssertionError("Invalid state: " + state);
            }
        }
    }

    public void writeBuffer(ByteBuffer buf, CompletionHandler handler) {
        boolean thisThreadShouldWrite = false;

        synchronized (monitor) {
            if (state == State.FINISHED_OR_ERROR) {
                if (handler != null) {
                    executor.execute(() ->  handler.failed(new IllegalStateException("ContentChannel already closed.")));
                }
                return;
            }

            responseContentQueue.addLast(new ResponseContentPart(buf, handler));
            switch (state) {
                case NOT_STARTED:
                    state = State.WAITING_FOR_WRITE_POSSIBLE_CALLBACK;
                    break;
                case WAITING_FOR_WRITE_POSSIBLE_CALLBACK:
                case WRITING_BUFFERS:
                    break;
                case WAITING_FOR_FIRST_BUFFER:
                case WAITING_FOR_SUBSEQUENT_BUFFER:
                    thisThreadShouldWrite = true;
                    state = State.WRITING_BUFFERS;
                    break;
                default:
                    throw new IllegalStateException("Invalid state " + state);
            }
        }

        if (thisThreadShouldWrite) {
            writeBuffersInQueueToOutputStream();
        }
    }

    public void close(CompletionHandler handler) {
        writeBuffer(CLOSE_STREAM_BUFFER, handler);
    }

    private void writeBuffersInQueueToOutputStream() {
        boolean lastOperationWasFlush = false;

        while (true) {
            ResponseContentPart contentPart;

            synchronized (monitor) {
                if (state == State.FINISHED_OR_ERROR) {
                    return;
                }

                assertStateIs(state, State.WRITING_BUFFERS);

                if (!outputStream.isReady()) {
                    state = State.WAITING_FOR_WRITE_POSSIBLE_CALLBACK;
                    return;
                }

                contentPart = responseContentQueue.pollFirst();

                if (contentPart == null && lastOperationWasFlush) {
                    state = State.WAITING_FOR_SUBSEQUENT_BUFFER;
                    return;
                }
            }

            try {
                boolean isFlush = contentPart == null;
                if (isFlush) {
                    outputStream.flush();
                    lastOperationWasFlush = true;
                    continue;
                }
                lastOperationWasFlush = false;

                if (contentPart.buf == CLOSE_STREAM_BUFFER) {
                    contentPart.handler.completed();
                    setFinished(Optional.empty());
                } else {
                    writeBufferToOutputStream(contentPart);
                }
            } catch (Throwable e) {
                setFinished(Optional.of(e));
            }
        }
    }

    private void setFinished(Optional<Throwable> e) {
        synchronized (monitor) {
            state = State.FINISHED_OR_ERROR;
            if (!responseContentQueue.isEmpty()) {
                failAllParts_holdingLock(e.orElse(new IllegalStateException("ContentChannel closed.")));
            }
        }

        assert !Thread.holdsLock(monitor);
        if (e.isPresent()) {
            finishedFuture.completeExceptionally(e.get());
        } else {
            finishedFuture.complete(null);
        }
    }

    private void failAllParts_holdingLock(Throwable e) {
        assert Thread.holdsLock(monitor);

        ArrayList<ResponseContentPart> failedParts = new ArrayList<>(responseContentQueue);
        responseContentQueue.clear();

        @SuppressWarnings("ThrowableInstanceNeverThrown")
        RuntimeException failReason = new RuntimeException("Failing due to earlier ServletOutputStream write failure", e);

        Consumer<ResponseContentPart> failCompletionHandler = responseContentPart ->
                runCompletionHandler_logOnExceptions(
                        () -> responseContentPart.handler.failed(failReason));

        executor.execute(
                () -> failedParts.forEach(failCompletionHandler));
    }

    private void writeBufferToOutputStream(ResponseContentPart contentPart) throws Throwable {
        try {
            ByteBuffer buffer = contentPart.buf;
            final int bytesToSend = buffer.remaining();
            try {
                if (buffer.hasArray()) {
                    outputStream.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
                } else {
                    final byte[] array = new byte[buffer.remaining()];
                    buffer.get(array);
                    outputStream.write(array);
                }
                metricReporter.successfulWrite(bytesToSend);
            } catch (Throwable throwable) {
                metricReporter.failedWrite();
                throw throwable;
            }
            contentPart.handler.completed(); //Might throw an exception, handling in the enclosing scope.
        } catch (Throwable e) {
            runCompletionHandler_logOnExceptions(() -> contentPart.handler.failed(e));
            throw e;
        }
    }

    private void runCompletionHandler_logOnExceptions(Runnable runnable) {
        assert !Thread.holdsLock(monitor);
        try {
            runnable.run();
        } catch (Throwable e) {
            log.log(Level.WARNING, "Unexpected exception from CompletionHandler.", e);
        }
    }

    private static void assertStateIs(State currentState, State expectedState) {
        if (currentState != expectedState) {
            throw createAndLogAssertionError("Expected state " + expectedState + ", got state " + currentState);
        }
    }

    private static AssertionError createAndLogAssertionError(String detailedMessage) {
        AssertionError error = new AssertionError(detailedMessage);
        log.log(Level.WARNING, "Assertion failed.", error);
        return error;
    }

    public void fail(Throwable t) {
        setFinished(Optional.of(t));
    }

    private final WriteListener writeListener = new WriteListener() {
        @Override
        public void onWritePossible() throws IOException {
            boolean shouldWriteBuffers = false;
            synchronized (monitor) {
                switch (state) {
                    case NOT_STARTED:
                        state = State.WAITING_FOR_FIRST_BUFFER;
                        break;
                    case WAITING_FOR_WRITE_POSSIBLE_CALLBACK:
                        state = State.WRITING_BUFFERS;
                        shouldWriteBuffers = true;
                        break;
                    case FINISHED_OR_ERROR:
                        return;
                    case WAITING_FOR_FIRST_BUFFER:
                    case WAITING_FOR_SUBSEQUENT_BUFFER:
                    case WRITING_BUFFERS:
                        throw createAndLogAssertionError("Invalid state: " + state);
                }
            }
            if (shouldWriteBuffers) {
                writeBuffersInQueueToOutputStream();
            }
        }

        @Override
        public void onError(Throwable t) {
            setFinished(Optional.of(t));
        }
    };

}
