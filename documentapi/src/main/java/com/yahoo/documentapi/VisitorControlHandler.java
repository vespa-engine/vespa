// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.vdslib.VisitorStatistics;

import java.time.Duration;

/**
 * A class for controlling a visitor supplied through visitor parameters when
 * creating the visitor session. The class defines callbacks for reporting
 * progress and that the visitor is done. If you want to reimplement the
 * default behavior of those callbacks, you can write your own subclass.
 *
 * @author Håkon Humberset
 */
public class VisitorControlHandler {
    /** Possible completion codes for visiting. */
    public enum CompletionCode {
        /** Visited all specified data successfully. */
        SUCCESS,
        /** Aborted by user. */
        ABORTED,
        /** Fatal failure. */
        FAILURE,
        /** Create visitor reply did not return within the specified timeframe, or the session timed out. */
        TIMEOUT
    };

    /**
     * The result of the visitor, containing a completion code and an optional
     * error message.
     */
    public class Result {
        public CompletionCode code;
        public String message;

        public String toString() {
            switch(code) {
            case SUCCESS:
                return "OK: " + message;
            case ABORTED:
                return "ABORTED: " + message;
            case FAILURE:
                return "FAILURE: " + message;
            case TIMEOUT:
                return "TIMEOUT: " + message;
            }

            return "Unknown error";
        }

        public CompletionCode getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    };

    private VisitorControlSession session;
    private ProgressToken currentProgress;
    private boolean completed = false;
    private Result result;
    private VisitorStatistics currentStatistics;

    /**
     * Called before the visitor starts. Override this method if you need
     * to reset local data. Remember to call the superclass' method as well.
     */
    public void reset() {
        synchronized (this) {
            session = null;
            currentProgress = null;
            completed = false;
            result = null;
        }
    }

    /**
     * Callback called when progress has changed.
     *
     * @param token the most recent progress token for this visitor
     */
    public void onProgress(ProgressToken token) {
        currentProgress = token;
    }

    /**
     * Callback for visitor error messages.
     *
     * @param message the error message
     */
    public void onVisitorError(String message) {
    }

    /**
     * Callback for visitor statistics updates.
     *
     * @param vs The current statistics for this visitor.
     */
    public void onVisitorStatistics(VisitorStatistics vs) {
        currentStatistics = vs;
    }

    /**
     * Returns true iff the statistics reported by the visiting session indicates at least one
     * bucket has been completely visited.
     *
     * Not thread safe, so should only be called on a quiescent session after waitUntilDone
     * has completed successfully.
     */
    public boolean hasVisitedAnyBuckets() {
        return ((currentStatistics != null) && (currentStatistics.getBucketsVisited() > 0));
    }

    /**
     * Callback called when the visitor is done.
     *
     * @param code the completion code
     * @param message an optional error message
     */
    public void onDone(CompletionCode code, String message) {
        synchronized (this) {
            completed = true;
            result = new Result();
            result.code = code;
            result.message = message;
            notifyAll();
        }
    }

    /** @param session the visitor session used for this visitor */
    public void setSession(VisitorControlSession session) {
        this.session = session;
    }

    /** @return Retrieves the last progress token gotten for this visitor. If visitor has not been started, returns null.*/
    public ProgressToken getProgress() { return currentProgress; }

    public VisitorStatistics getVisitorStatistics() { return currentStatistics; }

    /** @return True if the visiting is done (either by error or success). */
    public boolean isDone() {
        synchronized (this) {
            return completed;
        }
    }

    /**
     * Waits until visiting is done, or the given timeout (in ms) expires.
     * Will wait forever if timeout is 0.
     *
     * @param timeout Maximum time duration to wait before returning.
     * @return True if visiting is done (either by error or success), false if session has timed out.
     * @throws InterruptedException If an interrupt signal was received while waiting.
     */
    public boolean waitUntilDone(Duration timeout) throws InterruptedException {
        synchronized (this) {
            if (completed) {
                return true;
            }
            if (timeout.isZero()) {
                while (!completed) {
                    wait();
                }
            } else {
                wait(timeout.toMillis());
            }
            return completed;
        }
    }

    /**
     * Waits until visiting is done, or the given timeout (in ms) expires.
     * Will wait forever if timeout is 0.
     *
     * @param timeoutMs The maximum amount of milliseconds to wait.
     * @return True if visiting is done (either by error or success), false if session has timed out.
     * @throws InterruptedException If an interrupt signal was received while waiting.
     *
     * TODO deprecate this in favor of waitUntilDone(Duration)
     */
    public boolean waitUntilDone(long timeoutMs) throws InterruptedException {
        return waitUntilDone(Duration.ofMillis(timeoutMs));
    }

    /**
     * Waits until visiting is done. Session timeout implicitly completes
     * the visitor session, but will set an unsuccessful result code.
     *
     * @throws InterruptedException If an interrupt signal was received while waiting.
     */
    public void waitUntilDone() throws InterruptedException {
        final boolean done = waitUntilDone(Duration.ZERO);
        assert done : "Infinite waitUntilDone timeout should always complete";
    }

    /**
     * Abort this visitor
     */
    public void abort() { session.abort(); }

    /**
       @return The result of the visiting, if done. If not done, returns null.
    */
    public Result getResult() { return result; };
}
