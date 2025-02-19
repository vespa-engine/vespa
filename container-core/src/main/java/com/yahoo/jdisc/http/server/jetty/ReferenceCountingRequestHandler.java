// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.SharedResource;
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
 * This class wraps a request handler and does reference counting on the request for every object that depends on the
 * request, such as the response handler, content channels and completion handlers. This ensures that requests (and
 * hence the current container) will be referenced until the end of the request handling - even with async handling in
 * non-framework threads - without requiring the application to handle this tedious work.
 *
 * @author bakksjo
 */
@SuppressWarnings("try")
class ReferenceCountingRequestHandler implements DelegatedRequestHandler {

    private static final Logger log = Logger.getLogger(ReferenceCountingRequestHandler.class.getName());

    final RequestHandler delegate;

    ReferenceCountingRequestHandler(RequestHandler delegate) {
        Objects.requireNonNull(delegate, "delegate");
        this.delegate = delegate;
    }

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler responseHandler) {
        try (final ResourceReference requestReference = request.refer(this)) {
            ContentChannel contentChannel;
            final ReferenceCountingResponseHandler referenceCountingResponseHandler
                    = new ReferenceCountingResponseHandler(request, new NullContentResponseHandler(responseHandler));
            try {
                contentChannel = delegate.handleRequest(request, referenceCountingResponseHandler);
                Objects.requireNonNull(contentChannel, "contentChannel");
            } catch (Throwable t) {
                try {
                    // The response handler might never be invoked, due to the exception thrown from handleRequest().
                    referenceCountingResponseHandler.unrefer();
                } catch (Throwable thrownFromUnrefer) {
                    log.log(Level.WARNING, "Unexpected problem", thrownFromUnrefer);
                }
                throw t;
            }
            return new ReferenceCountingContentChannel(request, contentChannel);
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

    private static class ReferenceCountingResponseHandler implements ResponseHandler {

        final SharedResource request;
        final ResourceReference requestReference;
        final ResponseHandler delegate;
        final AtomicBoolean closed = new AtomicBoolean(false);

        ReferenceCountingResponseHandler(SharedResource request, ResponseHandler delegate) {
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
                return new ReferenceCountingContentChannel(request, contentChannel);
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        /**
         * Close the reference that is normally closed by {@link #handleResponse(Response)}.
         *
         * This is to be used in error situations, where handleResponse() may not be invoked.
         */
        public void unrefer() {
            if (closed.getAndSet(true)) {
                // This simply means that handleResponse() has been run, in which case we are
                // guaranteed that the reference is closed.
                return;
            }
            requestReference.close();
        }
    }

    private static class ReferenceCountingContentChannel implements ContentChannel {

        final SharedResource request;
        final ResourceReference requestReference;
        final ContentChannel delegate;

        ReferenceCountingContentChannel(SharedResource request, ContentChannel delegate) {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(delegate, "delegate");
            this.request = request;
            this.delegate = delegate;
            this.requestReference = request.refer(this);
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler completionHandler) {
            final CompletionHandler referenceCountingCompletionHandler
                    = new ReferenceCountingCompletionHandler(request, completionHandler);
            try {
                delegate.write(buf, referenceCountingCompletionHandler);
            } catch (Throwable t) {
                try {
                    referenceCountingCompletionHandler.failed(t);
                } catch (AlreadyCompletedException ignored) {
                } catch (Throwable failFailure) {
                    log.log(Level.WARNING, "Failure during call to CompletionHandler.failed()", failFailure);
                }
                throw t;
            }
        }

        @Override
        public void close(CompletionHandler completionHandler) {
            final CompletionHandler referenceCountingCompletionHandler
                    = new ReferenceCountingCompletionHandler(request, completionHandler);
            try (final ResourceReference ref = requestReference) {
                delegate.close(referenceCountingCompletionHandler);
            } catch (Throwable t) {
                try {
                    referenceCountingCompletionHandler.failed(t);
                } catch (AlreadyCompletedException ignored) {
                } catch (Throwable failFailure) {
                    log.log(Level.WARNING, "Failure during call to CompletionHandler.failed()", failFailure);
                }
                throw t;
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private static class AlreadyCompletedException extends IllegalStateException {
        public AlreadyCompletedException(final CompletionHandler completionHandler) {
            super(completionHandler + " is already called.");
        }
    }

    private static class ReferenceCountingCompletionHandler implements CompletionHandler {

        final ResourceReference requestReference;
        final CompletionHandler delegate;
        final AtomicBoolean closed = new AtomicBoolean(false);

        public ReferenceCountingCompletionHandler(SharedResource request, CompletionHandler delegate) {
            this.delegate = delegate;
            this.requestReference = request.refer(this);
        }

        @Override
        public void completed() {
            if (closed.getAndSet(true)) {
                throw new AlreadyCompletedException(delegate);
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
                throw new AlreadyCompletedException(delegate);
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
}
