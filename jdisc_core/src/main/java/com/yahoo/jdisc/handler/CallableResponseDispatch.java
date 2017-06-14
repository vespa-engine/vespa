// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Response;

import java.util.concurrent.Callable;

/**
 * <p>This is a convenient subclass of {@link ResponseDispatch} that implements the {@link Callable} interface. This
 * should be used in place of {@link ResponseDispatch} if you intend to schedule its execution. Because {@link #call()}
 * does not return until the entirety of the {@link Response} and its content have been consumed, you can use the
 * <tt>Future</tt> return value of <tt>ExecutorService.submit(Callable)</tt> to wait for it to complete.</p>
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public abstract class CallableResponseDispatch extends ResponseDispatch implements Callable<Boolean> {

    private final ResponseHandler handler;

    /**
     * <p>Constructs a new instances of this class over the given {@link ResponseHandler}. Invoking {@link #call()} will
     * dispatch to this handler.</p>
     *
     * @param handler The ResponseHandler to dispatch to.
     */
    public CallableResponseDispatch(ResponseHandler handler) {
        this.handler = handler;
    }

    @Override
    public final Boolean call() throws Exception {
        return dispatch(handler).get();
    }
}
