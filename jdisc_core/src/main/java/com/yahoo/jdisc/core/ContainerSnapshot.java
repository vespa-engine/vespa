// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.NullContent;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.util.Objects;

/**
 * @author Simon Thoresen Hult
 */
class ContainerSnapshot extends AbstractResource implements Container {

    private final TimeoutManagerImpl timeoutMgr;
    private final ActiveContainer container;
    private final ResourceReference containerReference;
    private final BindingSet<RequestHandler> serverBindings;
    private final BindingSet<RequestHandler> clientBindings;

    ContainerSnapshot(ActiveContainer container, BindingSet<RequestHandler> serverBindings,
                      BindingSet<RequestHandler> clientBindings, Object context)
    {
        this.timeoutMgr = container.timeoutManager();
        this.container = container;
        this.serverBindings = serverBindings;
        this.clientBindings = clientBindings;
        this.containerReference = container.refer(context);
    }

    @Override
    public <T> T getInstance(Class<T> type) {
        return container.guiceInjector().getInstance(type);
    }

    @Override
    public RequestHandler resolveHandler(Request request) {
        BindingMatch<RequestHandler> match = request.isServerRequest() ? serverBindings.match(request.getUri())
                                                                       : clientBindings.match(request.getUri());
        if (match == null) {
            return null;
        }
        request.setBindingMatch(match);
        RequestHandler ret = new NullContentRequestHandler(match.target());
        if (request.getTimeoutManager() == null) {
            ret = timeoutMgr.manageHandler(ret);
        }
        return ret;
    }

    @Override
    protected void destroy() {
        containerReference.close();
    }

    @Override
    public long currentTimeMillis() {
        return timeoutMgr.timer().currentTimeMillis();
    }

    private static class NullContentRequestHandler implements DelegatedRequestHandler {

        final RequestHandler delegate;

        NullContentRequestHandler(RequestHandler delegate) {
            Objects.requireNonNull(delegate, "delegate");
            this.delegate = delegate;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler responseHandler) {
            ContentChannel content = delegate.handleRequest(request, responseHandler);
            if (content == null) {
                content = NullContent.INSTANCE;
            }
            return content;
        }

        @Override
        public void handleTimeout(Request request, ResponseHandler responseHandler) {
            delegate.handleTimeout(request, responseHandler);
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
    }
}
