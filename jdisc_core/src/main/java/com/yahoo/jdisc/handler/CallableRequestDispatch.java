// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Response;

import java.util.concurrent.Callable;

/**
 * This is a convenient subclass of {@link RequestDispatch} that implements the {@link Callable} interface. This
 * should be used in place of {@link RequestDispatch} if you intend to schedule its execution. Because {@link #call()}
 * does not return until a {@link Response} becomes available, you can use the <code>Future</code> return value of
 * <code>ExecutorService.submit(Callable)</code> to wait for it.
 *
 * @author Simon Thoresen Hult
 */
public abstract class CallableRequestDispatch extends RequestDispatch implements Callable<Response> {

    @Override
    public final Response call() throws Exception {
        return dispatch().get();
    }

}
