// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.eclipse.jetty.server.handler.gzip;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handler that ensures {@link GzipRequest#destroy()} is called for all gzip encoded requests
 * when the request completes, regardless of whether response compression occurred.
 * Works around a lifecycle issue in {@link GzipHandler} where inflated requests that
 * are not deflated do not have their destroy() method called, leading inflater instances not being recycled.
 *
 * @author bjorncs
 */
public class GzipRequestCleanupHandler extends Handler.Wrapper {

    public GzipRequestCleanupHandler(Handler handler) {
        super(handler);
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        var next = getHandler();
        if (next == null) return false;
        // GzipHandler will take care of invoking destroy() if response compression occurs
        if (request instanceof GzipRequest gzipRequest && !(response instanceof GzipResponseAndCallback)) {
            var cleanupCallback = new CleanupCallback(gzipRequest, callback);
            var result = next.handle(request, response, cleanupCallback);
            if (!result) cleanupCallback.shouldInvokeDestroy.set(false); // GzipHandler will invoke destroy for us, terminate early (if possible)
            return result;
        } else {
            return next.handle(request, response, callback);
        }
    }

    private static class CleanupCallback implements Callback {
        private final GzipRequest gzipRequest;
        private final Callback delegate;
        private final AtomicBoolean shouldInvokeDestroy = new AtomicBoolean(true);

        CleanupCallback(GzipRequest gzipRequest, Callback delegate) {
            this.gzipRequest = gzipRequest;
            this.delegate = delegate;
        }

        @Override
        public void succeeded() {
            try {
                delegate.succeeded();
            } finally {
                cleanup();
            }
        }

        @Override
        public void failed(Throwable x) {
            try {
                delegate.failed(x);
            } finally {
                cleanup();
            }
        }

        @Override public InvocationType getInvocationType() { return delegate.getInvocationType(); }

        private void cleanup() {
            if (shouldInvokeDestroy.compareAndSet(true, false)) {
                gzipRequest.destroy();
            }
        }
    }
}
