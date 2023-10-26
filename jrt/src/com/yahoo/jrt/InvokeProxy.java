// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

/**
 * Invokes asynchronous JRT requests in a blocking method, that can be aborted by calling shutdown().
 * This can be used when one would like to use invokeSync(), but when abortion is required.
 * <p>
 * This class is thread safe.
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 */
//@ThreadSafe
public class InvokeProxy  {

    private boolean active  = true;
    private Request pending = null;

    public Request invoke(Request req, Target target, double timeout) {
        SingleRequestWaiter waiter = null;
        synchronized (this) {
            if (active) {
                waiter = new SingleRequestWaiter();
                target.invokeAsync(req, timeout, waiter);
                pending = req;
            } else {
                req.setError(ErrorCode.ABORT, "Aborted by user");
            }
        }
        if (waiter != null) {
            waiter.waitDone();
            synchronized (this) {
                pending = null;
            }
        }
        return req;
    }

    public void shutdown() {
        synchronized (this) {
            active = false;
            if (pending != null) {
                pending.abort();
            }
        }
    }

}
