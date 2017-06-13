// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncCallback;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperationImpl;
import com.yahoo.vespa.clustercontroller.utils.util.Clock;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class TimeoutHandler<V extends HttpResult> extends AsyncHttpClientWithBase<V> {
    public static class InternalRequest<V extends HttpResult> extends AsyncOperationImpl<V> {
        final AsyncOperation<V> operation;
        long startTime;
        long timeout;

        public InternalRequest(AsyncOperation<V> op, long startTime, long timeout) {
            super(op.getName(), op.getDescription());
            this.operation = op;
            this.startTime = startTime;
            this.timeout = timeout;
            op.register(new AsyncCallback<V>() {
                @Override
                public void done(AsyncOperation<V> op) {
                    if (!isDone()) {
                        if (op.isSuccess()) {
                            setResult(op.getResult());
                        } else {
                            setFailure(op.getCause(), op.getResult());
                        }
                    }
                }
            });
        }

        public long getTimeoutTime() { return startTime + timeout; }

        public void handleTimeout(long currentTime) {
            long timePassed = currentTime - startTime;
            this.setFailure(new TimeoutException("Operation timeout. " + timePassed + " ms since operation was issued. Timeout was " + timeout + " ms."));
            operation.cancel();
        }

        @Override
        public boolean cancel() { return operation.cancel(); }
        @Override
        public boolean isCanceled() { return operation.isCanceled(); }
        @Override
        public Double getProgress() { return (isDone() ? Double.valueOf(1.0) : operation.getProgress()); }
    }

    public static class ChangeLogger {
        private InternalRequest lastTimeoutLogged = null;
        private boolean emptyLogged = true;

        public void logChanges(TreeMap<Long, InternalRequest> requests) {
            if (requests.isEmpty()) {
                if (!emptyLogged) {
                    log.finest("No more pending requests currently.");
                    emptyLogged = true;
                }
            } else {
                emptyLogged = false;
                InternalRequest r = requests.firstEntry().getValue();
                if (lastTimeoutLogged == null || !lastTimeoutLogged.equals(r)) {
                    lastTimeoutLogged = r;
                    log.finest("Next operation to possibly timeout will do so at " + r.getTimeoutTime());
                }
            }
        }
    }

    private final static Logger log = Logger.getLogger(TimeoutHandler.class.getName());
    private final TreeMap<Long, InternalRequest> requests = new TreeMap<>();
    private final ChangeLogger changeLogger = new ChangeLogger();
    private final Clock clock;
    private boolean run = true;
    private Runnable timeoutHandler = new Runnable() {
        @Override
        public void run() {
            log.fine("Starting timeout monitor thread");
            while (true) {
                performTimeoutHandlerTick();
                synchronized (clock) {
                    try{ clock.wait(100); } catch (InterruptedException e) {}
                    if (!run) break;
                }
            }
            log.fine("Stopped timeout monitor thread");
        }
    };

    public TimeoutHandler(Executor executor, Clock clock, AsyncHttpClient<V> client) {
        super(client);
        this.clock = clock;
        executor.execute(timeoutHandler);
    }

    @Override
    public void close() {
        synchronized (clock) {
            run = false;
            clock.notifyAll();
        }
        synchronized (requests) {
            for (InternalRequest r : requests.values()) {
                r.operation.cancel();
                r.setFailure(new TimeoutException("Timeout handler shutting down. Shutting down all requests monitored."));
            }
            requests.clear();
        }
    }

    @Override
    public AsyncOperation<V> execute(HttpRequest r) {
        AsyncOperation<V> op = super.execute(r);
        InternalRequest<V> request = new InternalRequest<>(op, clock.getTimeInMillis(), r.getTimeoutMillis());
        synchronized (requests) {
            requests.put(request.getTimeoutTime(), request);
        }
        return request;
    }

    void performTimeoutHandlerTick() {
        synchronized (requests) {
            removeCompletedRequestsFromTimeoutList();
            handleTimeoutsAtTime(clock.getTimeInMillis());
            changeLogger.logChanges(requests);
        }
    }

    private void removeCompletedRequestsFromTimeoutList() {
        while (!requests.isEmpty() && requests.firstEntry().getValue().operation.isDone()) {
            requests.remove(requests.firstEntry().getKey());
            log.finest("Removed completed request from operation timeout list.");
        }
    }

    private void handleTimeoutsAtTime(long currentTime) {
        Map<Long, InternalRequest> timeouts = requests.subMap(0l, currentTime + 1);
        for (InternalRequest r : timeouts.values()) {
            r.handleTimeout(currentTime);
            requests.values().remove(r);
        }
    }
}
