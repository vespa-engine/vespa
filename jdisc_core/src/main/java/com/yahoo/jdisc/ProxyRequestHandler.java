// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.NullContent;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
* @author bakksjo
*/
class ProxyRequestHandler implements DelegatedRequestHandler {

    private static final CompletionHandler IGNORED_COMPLETION = new IgnoredCompletion();
    private static final Logger log = Logger.getLogger(ProxyRequestHandler.class.getName());

    final RequestHandler delegate;

    ProxyRequestHandler(RequestHandler delegate) {
        Objects.requireNonNull(delegate, "delegate");
        this.delegate = delegate;
    }

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler responseHandler) {
        try (ResourceReference requestReference = request.refer()) {
            ContentChannel contentChannel;
            ResponseHandler proxyResponseHandler = new ProxyResponseHandler(
                    request, new NullContentResponseHandler(responseHandler));
            try {
                contentChannel = delegate.handleRequest(request, proxyResponseHandler);
                Objects.requireNonNull(contentChannel, "contentChannel");
            } catch (Throwable t) {
                try {
                    proxyResponseHandler
                            .handleResponse(new Response(Response.Status.INTERNAL_SERVER_ERROR, t))
                            .close(IGNORED_COMPLETION);
                } catch (Throwable ignored) {
                    // empty
                }
                throw t;
            }
            return new ProxyContentChannel(request, contentChannel);
        }
    }

    @Override
    public void handleTimeout(Request request, ResponseHandler responseHandler) {
        delegate.handleTimeout(request, new NullContentResponseHandler(responseHandler));
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

    private static class ProxyResponseHandler implements ResponseHandler {

        final SharedResource request;
        final ResourceReference requestReference;
        final ResponseHandler delegate;
        final AtomicBoolean closed = new AtomicBoolean(false);

        ProxyResponseHandler(SharedResource request, ResponseHandler delegate) {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(delegate, "delegate");
            this.request = request;
            this.delegate = delegate;
            this.requestReference = request.refer(this);
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            if (closed.getAndSet(true)) {
                throw new IllegalStateException(delegate + " is already called.");
            }
            try (final ResourceReference ref = requestReference) {
                ContentChannel contentChannel = delegate.handleResponse(response);
                Objects.requireNonNull(contentChannel, "contentChannel");
                return new ProxyContentChannel(request, contentChannel);
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private static class ProxyContentChannel implements ContentChannel {

        final SharedResource request;
        final ResourceReference requestReference;
        final ContentChannel delegate;

        ProxyContentChannel(SharedResource request, ContentChannel delegate) {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(delegate, "delegate");
            this.request = request;
            this.delegate = delegate;
            this.requestReference = request.refer(this);
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler completionHandler) {
            ProxyCompletionHandler proxyCompletionHandler = new ProxyCompletionHandler(request, completionHandler);
            try {
                delegate.write(buf, proxyCompletionHandler);
            } catch (Throwable t) {
                try {
                    proxyCompletionHandler.failed(t);
                } catch (Throwable ignored) {
                    // empty
                }
                throw t;
            }
        }

        @Override
        public void close(CompletionHandler completionHandler) {
            final ProxyCompletionHandler proxyCompletionHandler
                    = new ProxyCompletionHandler(request, completionHandler);
            try (final ResourceReference ref = requestReference) {
                delegate.close(proxyCompletionHandler);
            } catch (Throwable t) {
                try {
                    proxyCompletionHandler.failed(t);
                } catch (Throwable ignored) {
                    // empty
                }
                throw t;
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private static class ProxyCompletionHandler implements CompletionHandler {

        final ResourceReference requestReference;
        final CompletionHandler delegate;
        final AtomicBoolean closed = new AtomicBoolean(false);

        public ProxyCompletionHandler(SharedResource request, CompletionHandler delegate) {
            this.delegate = delegate;
            this.requestReference = request.refer(this);
        }

        @Override
        public void completed() {
            if (closed.getAndSet(true)) {
                throw new IllegalStateException(delegate + " is already called.");
            }
            try {
                if (delegate != null) {
                    delegate.completed();
                }
            } finally {
                requestReference.close();
            }
        }

        @Override
        public void failed(Throwable t) {
            if (closed.getAndSet(true)) {
                throw new IllegalStateException(delegate + " is already called.");
            }
            try (final ResourceReference ref = requestReference) {
                if (delegate != null) {
                    delegate.failed(t);
                } else {
                    log.log(Level.WARNING, "Uncaught completion failure.", t);
                }
            }
        }

        @Override
        public String toString() {
            return String.valueOf(delegate);
        }
    }

    private static class NullContentResponseHandler implements ResponseHandler {

        final ResponseHandler delegate;

        NullContentResponseHandler(ResponseHandler delegate) {
            Objects.requireNonNull(delegate, "delegate");
            this.delegate = delegate;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            ContentChannel contentChannel = delegate.handleResponse(response);
            if (contentChannel == null) {
                contentChannel = NullContent.INSTANCE;
            }
            return contentChannel;
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private static class IgnoredCompletion implements CompletionHandler {

        @Override
        public void completed() {
            // ignore
        }

        @Override
        public void failed(Throwable t) {
            // ignore
        }
    }
}
