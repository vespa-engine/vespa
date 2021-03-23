// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;

/**
 * @author bjorncs
 */
public abstract class RestApiRequestHandler<T extends RestApiRequestHandler<T>> extends LoggingRequestHandler {

    private final RestApi restApi;

    @FunctionalInterface public interface RestApiProvider<T> { RestApi createRestApi(T self); }

    /**
     * RestApi will usually refer to handler methods of subclass, which are not accessible before super constructor has completed.
     * This is hack to leak reference to subclass instance's "this" reference.
     * Caller must ensure that provider instance does not try to access any uninitialized fields.
     */
    @SuppressWarnings("unchecked")
    protected RestApiRequestHandler(LoggingRequestHandler.Context context, RestApiProvider<T> provider) {
        super(context);
        this.restApi = provider.createRestApi((T)this);
    }

    protected RestApiRequestHandler(LoggingRequestHandler.Context context, RestApi restApi) {
        super(context);
        this.restApi = restApi;
    }

    @Override public final HttpResponse handle(HttpRequest request) { return restApi.handleRequest(request); }

    protected RestApi restApi() { return restApi; }
}
