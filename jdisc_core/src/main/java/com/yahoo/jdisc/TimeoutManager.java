// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.yahoo.jdisc.handler.RequestHandler;

import java.util.concurrent.TimeUnit;

/**
 * <p>This interface provides a callback for when the {@link Request#setTimeout(long, TimeUnit)} is invoked. If no such
 * handler is registered at the time where the target {@link RequestHandler} is called, the default timeout manager will
 * be injected.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface TimeoutManager {

    /**
     * Schedule timeout management for a request.
     * This is called by a request whenever {@link Request#setTimeout(long, TimeUnit)} is invoked;
     * this may be called multiple times for the same {@link Request}.
     *
     * @param request the request whose timeout to schedule
     */
    public void scheduleTimeout(Request request);

    /**
     * Unschedule timeout management for a previously scheduled request.
     * This is called whenever a request is cancelled, and the purpose is to free up
     * resources taken by the implementation of this associated with the request.
     * <p>
     * This is only called once for a request, and only after at least one scheduleTimeout call.
     * <p>
     * The default implementation of this does nothing.
     *
     * @param request the previously scheduled timeout
     */
    default public void unscheduleTimeout(Request request) {
    }

}
