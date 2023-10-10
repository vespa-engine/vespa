// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.SharedResource;
import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.UriPattern;

import java.util.concurrent.TimeUnit;

/**
 * This interface defines a component that is capable of acting as a handler for a {@link Request}. To activate a
 * RequestHandler it must be {@link BindingRepository#bind(String, Object) bound} to a {@link UriPattern} within a
 * {@link ContainerBuilder}, and that builder must be {@link ContainerActivator#activateContainer(ContainerBuilder)
 * activated}.
 *
 * @author Simon Thoresen Hult
 */
public interface RequestHandler extends SharedResource {

    /**
     * <p>This method will process the given {@link Request} and return a {@link ContentChannel} into which the caller
     * can write the Request's content. For every call to this method, the implementation must call the provided {@link
     * ResponseHandler} exactly once.</p>
     *
     * <p>Notice that unless this method throws an Exception, a reference to the currently active {@link Container}
     * instance is kept internally until {@link ResponseHandler#handleResponse(Response)} has been called. This ensures
     * that the configured environment of the Request is stable throughout its lifetime. Failure to call back with a
     * Response will prevent the release of that reference, and therefore prevent the corresponding Container from ever
     * shutting down. The requirement to call {@link ResponseHandler#handleResponse(Response)} is regardless of any
     * subsequent errors that may occur while working with the returned ContentChannel.</p>
     *
     * @param request The Request to handle.
     * @param handler The handler to pass the corresponding {@link Response} to.
     * @return The ContentChannel to write the Request content to. Notice that the ContentChannel itself also holds a
     *         Container reference, so failure to close this will prevent the Container from ever shutting down.
     */
    ContentChannel handleRequest(Request request, ResponseHandler handler);

    /**
     * <p>This method is called by the {@link Container} when a {@link Request} that was previously accepted by {@link
     * #handleRequest(Request, ResponseHandler)} has timed out. If the Request has no timeout (i.e. {@link
     * Request#getTimeout(TimeUnit)} returns <em>null</em>), then this method is never called.</p>
     *
     * <p>The given {@link ResponseHandler} is the same ResponseHandler that was initially passed to the {@link
     * #handleRequest(Request, ResponseHandler)} method, and it is guarded by a volatile boolean so that only the first
     * call to {@link ResponseHandler#handleResponse(Response)} is actually passed on. This means that you do NOT need
     * to manage the ResponseHandlers yourself to prevent a late Response from calling the same ResponseHandler.</p>
     *
     * <p>Notice that you MUST call {@link ResponseHandler#handleResponse(Response)} as a reaction to having this method
     * invoked. Failure to do so will prevent the Container from ever shutting down.</p>
     *
     * @param request The Request that has timed out.
     * @param handler The handler to pass the timeout {@link Response} to.
     * @see Response#dispatchTimeout(ResponseHandler)
     */
    void handleTimeout(Request request, ResponseHandler handler);

}
