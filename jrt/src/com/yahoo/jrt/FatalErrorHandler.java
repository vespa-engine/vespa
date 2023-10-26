// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * Interface used to report fatal errors (internal thread
 * unwinding). If the application wants to be notified of these
 * errors, it must register a handler with the {@link Transport}
 * constructor.
 **/
public interface FatalErrorHandler {

    /**
     * Invoked when an internal thread crashes due to thread
     * unwinding.
     *
     * @param problem the throwable causing the problem
     * @param context the object owning the crashed thread
     **/
    public void handleFailure(Throwable problem, Object context);
}
