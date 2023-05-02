// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Inject;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.TimeoutManager;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public class TimeoutManagerImpl {

    private static final ContentChannel IGNORED_CONTENT = new IgnoredContent();
    private static final Logger log = Logger.getLogger(TimeoutManagerImpl.class.getName());
    private final ScheduledQueue scheduler;
    private final Thread thread;
    private final Timer timer;
    private final AtomicBoolean done = new AtomicBoolean(false);

    @Inject
    public TimeoutManagerImpl(ThreadFactory factory, Timer timer) {
        this.thread = factory.newThread(new ManagerTask());
        this.thread.setName(getClass().getName());
        this.timer = timer;
        this.scheduler = new ScheduledQueue(timer.currentTimeMillis());
    }

    public void start() {
        thread.start();
    }

    public void shutdown() {
        synchronized (done) {
            done.set(true);
            done.notify();
        }
        try {
            thread.join();
        } catch (InterruptedException e) {}
    }

    public RequestHandler manageHandler(RequestHandler handler) {
        return new ManagedRequestHandler(handler);
    }

    int queueSize() { return scheduler.queueSize(); }

    Timer timer() {
        return timer;
    }

    void checkTasks(long currentTimeMillis) {
        Queue<Object> queue = new LinkedList<>();
        scheduler.drainTo(currentTimeMillis, queue);
        while (!queue.isEmpty()) {
            TimeoutHandler timeoutHandler = (TimeoutHandler)queue.poll();
            invokeTimeout(timeoutHandler.requestHandler, timeoutHandler.request, timeoutHandler);
        }
    }

    private void invokeTimeout(RequestHandler requestHandler, Request request, ResponseHandler responseHandler) {
        try {
            requestHandler.handleTimeout(request, responseHandler);
        } catch (Exception e) {
            log.log(Level.WARNING, "Ignoring exception thrown by " + requestHandler.getClass().getName() +
                                   " in timeout manager.", e);
        }
        if (Thread.currentThread().isInterrupted()) {
            log.log(Level.WARNING, "Ignoring interrupt signal from " + requestHandler.getClass().getName() +
                                   " in timeout manager.");
            Thread.interrupted();
        }
    }

    private class ManagerTask implements Runnable {

        boolean oneMoreCheck(long timeoutMS) {
            synchronized (done) {
                if (!done.get()) {
                    try {
                        done.wait(timeoutMS);
                    } catch (InterruptedException e) {
                        log.log(Level.WARNING, "Ignoring interrupt signal in timeout manager.", e);
                    }
                }
            }
            return ! done.get();
        }

        @Override
        public void run() {
            Duration desiredTimeout = Duration.ofMillis(ScheduledQueue.MILLIS_PER_SLOT);
            Duration actualTimeout = SystemTimer.adjustTimeoutByDetectedHz(desiredTimeout);
            while (oneMoreCheck(actualTimeout.toMillis())) {
                checkTasks(timer.currentTimeMillis());
            }
        }
    }

    private class ManagedRequestHandler implements DelegatedRequestHandler {

        final RequestHandler delegate;

        ManagedRequestHandler(RequestHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler responseHandler) {
            TimeoutHandler timeoutHandler = new TimeoutHandler(request, delegate, responseHandler);
            request.setTimeoutManager(timeoutHandler);
            try {
                return delegate.handleRequest(request, timeoutHandler);
            } catch (Throwable throwable) {
                //This is only needed when this method is invoked outside of Request.connect,
                //and that seems to be the case for jetty right now.
                //To prevent this from being called outside Request.connect,
                //manageHandler() and com.yahoo.jdisc.Container.resolveHandler() must also be made non-public.
                //
                //The underlying framework will handle the request,
                //the application code is no longer responsible for calling responseHandler.handleResponse.
                timeoutHandler.unscheduleTimeout();
                throw throwable;
            }
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler responseHandler) {
            delegate.handleTimeout(request, responseHandler);
        }

        @Override
        public ResourceReference refer() {
            return delegate.refer();
        }

        @Override
        public ResourceReference refer(Object context) {
            return delegate.refer(context);
        }

        @Override
        public void release() {
            delegate.release();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public RequestHandler getDelegate() {
            return delegate;
        }
    }

    private class TimeoutHandler implements ResponseHandler, TimeoutManager {

        final ResponseHandler responseHandler;
        final RequestHandler requestHandler;
        final Request request;
        ScheduledQueue.Entry timeoutQueueEntry = null;
        boolean responded = false;

        TimeoutHandler(Request request, RequestHandler requestHandler, ResponseHandler responseHandler) {
            this.request = request;
            this.requestHandler = requestHandler;
            this.responseHandler = responseHandler;
        }

        @Override
        public synchronized void scheduleTimeout(Request request) {
            if (responded) {
                return;
            }
            if (timeoutQueueEntry == null) {
                timeoutQueueEntry = scheduler.newEntry(this);
            }
            timeoutQueueEntry.scheduleAt(request.creationTime(TimeUnit.MILLISECONDS) + request.getTimeout(TimeUnit.MILLISECONDS));
        }

        synchronized void unscheduleTimeout() {
            if (!responded && timeoutQueueEntry != null) {
                timeoutQueueEntry.unschedule();
                //guard against unscheduling from ManagedRequestHandler.handleRequest catch block
                //followed by unscheduling in another thread from TimeoutHandler.handleResponse
                timeoutQueueEntry = null;
            }
        }

        @Override
        public void unscheduleTimeout(Request request) {
            unscheduleTimeout();
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            synchronized (this) {
                unscheduleTimeout();
                if (responded) {
                    return IGNORED_CONTENT;
                }
                responded = true;
            }
            return responseHandler.handleResponse(response);
        }

        @Override
        public String toString() {
            return responseHandler.toString();
        }
    }

    private static class IgnoredContent implements ContentChannel {

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            if (handler == null) {
                return;
            }
            try {
                handler.completed();
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Ignoring exception thrown by " + handler.getClass().getName() +
                                       " in timeout manager.", e);
            }
        }

        @Override
        public void close(CompletionHandler handler) {
            if (handler == null) {
                return;
            }
            try {
                handler.completed();
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Ignoring exception thrown by " + handler.getClass().getName() +
                                       " in timeout manager.", e);
            }
        }
    }
}
