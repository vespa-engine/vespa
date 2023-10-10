// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * Interface used to wait for the completion of a {@link
 * Request}. This interface is used with the {@link Target#invokeAsync
 * Target.invokeAsync} method.
 **/
public interface RequestWaiter {

    /**
     * Invoked when a request has completed.
     *
     * @param req the completed request
     **/
    public void handleRequestDone(Request req);
}
