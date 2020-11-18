// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.rendering;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.processing.Response;

import java.util.concurrent.Executor;

/**
 * Superclass of all asynchronous renderers.
 * Subclasses this to receive an executor and the network wiring necessary to respectively
 * run callback listeners and close up the channel when the response is complete.
 *
 * @author bratseth
 */
public abstract class AsynchronousRenderer <RESPONSE extends Response> extends Renderer<RESPONSE> {

    /**
     * Exposes JDisc wiring to ensure asynchronous cleanup.
     *
     * @param channel the channel to the client receiving the response
     * @param completionHandler the JDisc completion handler which will be invoked at the end of the rendering
     * @throws IllegalStateException if attempted invoked more than once
     */
    public abstract void setNetworkWiring(ContentChannel channel, CompletionHandler completionHandler);

}
