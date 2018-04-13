// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.application.UriPattern;
import com.yahoo.jdisc.handler.BindingNotFoundException;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDeniedException;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.service.ServerProvider;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>This class represents a single request (which may have any content model that a {@link ServerProvider} chooses to
 * implement). The {@link #uri URI} is used by the {@link Container} to route it to the appropriate {@link
 * RequestHandler}, which in turn will provide a {@link ContentChannel} to write content to.</p>
 *
 * <p>To ensure application consistency throughout the lifetime of a Request, the Request itself holds an active
 * reference to the Container for which it was created. This has the unfortunate side-effect of requiring the creator of
 * a Request to do explicit reference counting during the setup of a content stream.</p>
 *
 * <p>For every successfully dispatched Request (i.e. a non-null ContentChannel has been retrieved), there will be
 * exactly one {@link Response} returned to the provided {@link ResponseHandler}.</p>
 *
 * @author Simon Thoresen
 * @see Container
 * @see Response
 */
public class Request extends AbstractResource {

    private final Map<String, Object> context = new HashMap<>();
    private final HeaderFields headers = new HeaderFields();
    private final Container container;
    private final Request parent;
    private final ResourceReference parentReference;
    private final long creationTime;
    private volatile boolean cancel = false;
    private BindingMatch<RequestHandler> bindingMatch;
    private TimeoutManager timeoutManager;
    private boolean serverRequest;
    private Long timeout;
    private URI uri;

    /**
     * <p>Creates a new instance of this class. As a {@link ServerProvider} you need to inject a {@link
     * CurrentContainer} instance at construction time and use that as argument to this method. As a {@link
     * RequestHandler} that needs to spawn child Requests, use the {@link #Request(Request, URI) other
     * constructor}.</p>
     *
     * <p>Because a Request holds an active reference to the owning {@link Container}, it is necessary to call {@link
     * #release()} once a {@link ContentChannel} has been established. Suggested usage:</p>
     *
     * <pre>
     * Request request = null;
     * ContentChannel content = null;
     * try {
     *     request = new Request(currentContainer, uri);
     *     (...)
     *     content = request.connect(responseHandler);
     * } finally {
     *    if (request != null) request.release();
     * }
     * content.write(...);
     * </pre>
     *
     * @param current The CurrentContainer for which this Request is created.
     * @param uri     The identifier of this request.
     */
    public Request(CurrentContainer current, URI uri) {
        container = current.newReference(uri);
        parent = null;
        parentReference = null;
        creationTime = container.currentTimeMillis();
        serverRequest = true;
        setUri(uri);
    }

    /**
     * <p>Creates a new instance of this class. As a {@link RequestHandler} you should use this method to spawn child
     * Requests of another. As a {@link ServerProvider} that needs to spawn new Requests, us the {@link
     * #Request(CurrentContainer, URI) other constructor}.</p>
     *
     * <p>Because a Request holds an active reference to the owning {@link Container}, it is necessary to call {@link
     * #release()} once a {@link ContentChannel} has been established. Suggested usage:</p>
     *
     * <pre>
     * Request request = null;
     * ContentChannel content = null;
     * try {
     *     request = new Request(parentRequest, uri);
     *     (...)
     *     content = request.connect(responseHandler);
     * } finally {
     *    if (request != null) request.release();
     * }
     * content.write(...);
     * </pre>
     *
     * @param parent The parent Request of this.
     * @param uri    The identifier of this request.
     */
    public Request(Request parent, URI uri) {
        this.parent = parent;
        this.parentReference = this.parent.refer();
        container = null;
        creationTime = parent.container().currentTimeMillis();
        serverRequest = false;
        setUri(uri);
    }

    /**
     * <p>Returns the {@link Container} for which this Request was created.</p>
     *
     * @return The container instance.
     */
    // TODO: Vespa 7 remove.
    public Container container() {
        return parent != null ? parent.container() : container;
    }

    /**
     * <p>Returns the Uniform Resource Identifier used by the {@link Container} to resolve the appropriate {@link
     * RequestHandler} for this Request.</p>
     *
     * @return The resource identifier.
     * @see #setUri(URI)
     */
    public URI getUri() {
        return uri;
    }

    /**
     * <p>Sets the Uniform Resource Identifier used by the {@link Container} to resolve the appropriate {@link
     * RequestHandler} for this Request. Because access to the URI is not guarded by any lock, any changes made after
     * calling {@link #connect(ResponseHandler)} might never become visible to other threads.</p>
     *
     * @param uri The URI to set.
     * @return This, to allow chaining.
     * @see #getUri()
     */
    public Request setUri(URI uri) {
        this.uri = uri.normalize();
        return this;
    }

    /**
     * <p>Returns whether or not this Request was created by a {@link ServerProvider}. The value of this is used by
     * {@link Container#resolveHandler(Request)} to decide whether to match against server- or client-bindings.</p>
     *
     * @return True, if this is a server request.
     */
    public boolean isServerRequest() {
        return serverRequest;
    }

    /**
     * <p>Sets whether or not this Request was created by a {@link ServerProvider}. The constructor that accepts a
     * {@link CurrentContainer} sets this to <em>true</em>, whereas the constructor that accepts a parent Request sets
     * this to <em>false</em>.</p>
     *
     * @param serverRequest Whether or not this is a server request.
     * @return This, to allow chaining.
     * @see #isServerRequest()
     */
    public Request setServerRequest(boolean serverRequest) {
        this.serverRequest = serverRequest;
        return this;
    }

    /**
     * <p>Returns the last resolved {@link BindingMatch}, or null if none has been resolved yet. This is set
     * automatically when calling the {@link Container#resolveHandler(Request)} method. The BindingMatch object holds
     * information about the match of this Request's {@link #getUri() URI} to the {@link UriPattern} of the resolved
     * {@link RequestHandler}. It allows you to reflect on the parts of the URI that were matched by wildcards in the
     * UriPattern.</p>
     *
     * @return The last resolved BindingMatch, or null.
     * @see #setBindingMatch(BindingMatch)
     * @see Container#resolveHandler(Request)
     */
    public BindingMatch<RequestHandler> getBindingMatch() {
        return bindingMatch;
    }

    /**
     * <p>Sets the last resolved {@link BindingMatch} of this Request. This is called by the {@link
     * Container#resolveHandler(Request)} method.</p>
     *
     * @param bindingMatch The BindingMatch to set.
     * @return This, to allow chaining.
     * @see #getBindingMatch()
     */
    public Request setBindingMatch(BindingMatch<RequestHandler> bindingMatch) {
        this.bindingMatch = bindingMatch;
        return this;
    }

    /**
     * <p>Returns the named application context objects. This data is not intended for network transport, rather they
     * are intended for passing shared data between components of an Application.</p>
     *
     * <p>Modifying the context map is a thread-unsafe operation -- any changes made after calling {@link
     * #connect(ResponseHandler)} might never become visible to other threads, and might throw
     * ConcurrentModificationExceptions in other threads.</p>
     *
     * @return The context map.
     */
    public Map<String, Object> context() {
        return context;
    }

    /**
     * <p>Returns the set of header fields of this Request. These are the meta-data of the Request, and are not applied
     * to any internal {@link Container} logic. As opposed to the {@link #context()}, the headers ARE intended for
     * network transport. Modifying headers is a thread-unsafe operation -- any changes made after calling {@link
     * #connect(ResponseHandler)} might never become visible to other threads, and might throw
     * ConcurrentModificationExceptions in other threads.</p>
     *
     * @return The header fields.
     */
    public HeaderFields headers() {
        return headers;
    }

    /**
     * <p>Sets a {@link TimeoutManager} to be called when {@link #setTimeout(long, TimeUnit)} is invoked. If a timeout
     * has already been set for this Request, the TimeoutManager is called before returning. This method will throw an
     * IllegalStateException if it has already been called.</p>
     *
     * <p><b>NOTE:</b> This is used by the default timeout management implementation, so unless you are replacing that
     * mechanism you should avoid calling this method. If you <em>do</em> want to replace that mechanism, you need to
     * call this method prior to calling the target {@link RequestHandler} (since that injects the default manager).</p>
     *
     * @param timeoutManager The manager to set.
     * @throws NullPointerException  If the TimeoutManager is null.
     * @throws IllegalStateException If another TimeoutManager has already been set.
     * @see #getTimeoutManager()
     * @see #setTimeout(long, TimeUnit)
     */
    public void setTimeoutManager(TimeoutManager timeoutManager) {
        Objects.requireNonNull(timeoutManager, "timeoutManager");
        if (this.timeoutManager != null) {
            throw new IllegalStateException("Timeout manager already set.");
        }
        this.timeoutManager = timeoutManager;
        if (timeout != null) {
            timeoutManager.scheduleTimeout(this);
        }
    }

    /**
     * <p>Returns the {@link TimeoutManager} of this request, or null if none has been assigned.</p>
     *
     * @return The TimeoutManager of this Request.
     * @see #setTimeoutManager(TimeoutManager)
     */
    public TimeoutManager getTimeoutManager() {
        return timeoutManager;
    }

    /**
     * <p>Sets the allocated time that this Request is allowed to exist before the corresponding call to {@link
     * ResponseHandler#handleResponse(Response)} must have been made. If no timeout value is assigned to a Request,
     * there will be no timeout.</p>
     *
     * <p>Once the allocated time has expired, unless the {@link ResponseHandler} has already been called, the {@link
     * RequestHandler#handleTimeout(Request, ResponseHandler)} method is invoked.</p>
     *
     * <p>Calls to {@link #isCancelled()} return <em>true</em> if timeout has been exceeded.</p>
     *
     * @param timeout The allocated amount of time.
     * @param unit    The time unit of the <em>timeout</em> argument.
     * @see #getTimeout(TimeUnit)
     * @see #timeRemaining(TimeUnit)
     */
    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = unit.toMillis(timeout);
        if (timeoutManager != null) {
            timeoutManager.scheduleTimeout(this);
        }
    }

    /**
     * <p>Returns the allocated number of time units that this Request is allowed to exist. If no timeout has been set
     * for this Request, this method returns <em>null</em>.</p>
     *
     * @param unit The unit to return the timeout in.
     * @return The timeout of this Request.
     * @see #setTimeout(long, TimeUnit)
     */
    public Long getTimeout(TimeUnit unit) {
        if (timeout == null) {
            return null;
        }
        return unit.convert(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * <p>Returns the time that this Request is allowed to exist. If no timeout has been set, this method will return
     * <em>null</em>.</p>
     *
     * @param unit The unit to return the time in.
     * @return The number of time units left until this Request times out, or <em>null</em>.
     */
    public Long timeRemaining(TimeUnit unit) {
        if (timeout == null) {
            return null;
        }
        return unit.convert(timeout - (container().currentTimeMillis() - creationTime), TimeUnit.MILLISECONDS);
    }

    /**
     * <p>Returns the time that this Request has existed so far.
     *
     * @param unit The unit to return the time in.
     * @return The number of time units elapsed since this Request was created.
     */
    public long timeElapsed(TimeUnit unit) {
        return unit.convert(container().currentTimeMillis() - creationTime, TimeUnit.MILLISECONDS);
    }

    /**
     * <p>Returns the time at which this Request was created. This is whatever value was returned by {@link
     * Timer#currentTimeMillis()} when constructing this.</p>
     *
     * @param unit The unit to return the time in.
     * @return The creation time of this Request.
     */
    public long creationTime(TimeUnit unit) {
        return unit.convert(creationTime, TimeUnit.MILLISECONDS);
    }

    /**
     * <p>Returns whether or not this Request has been cancelled. This can be thought of as the {@link
     * Thread#isInterrupted()} of Requests - it does not enforce anything in ways of blocking the Request, it is simply
     * a signal to allow the developer to break early if the Request has already been dropped.</p>
     *
     * <p>This method will also return <em>true</em> if the Request has a non-null timeout, and that timeout has
     * expired.</p>
     *
     * <p>Finally, this method will also return <em>true</em> if this Request has a parent Request that has been
     * cancelled.</p>
     *
     * @return True if this Request has timed out or been cancelled.
     * @see #cancel()
     * @see #setTimeout(long, TimeUnit)
     */
    public boolean isCancelled() {
        if (cancel) {
            return true;
        }
        if (timeout != null && timeRemaining(TimeUnit.MILLISECONDS) <= 0) {
            return true;
        }
        if (parent != null && parent.isCancelled()) {
            return true;
        }
        return false;
    }

    /**
     * <p>Mark this request as cancelled and frees any resources held by the request if possible.
     * All subsequent calls to {@link #isCancelled()} on this Request return <em>true</em>.</p>
     *
     * @see #isCancelled()
     */
    public void cancel() {
        if (cancel) return;

        if (timeoutManager != null && timeout != null)
            timeoutManager.unscheduleTimeout(this);
        cancel = true;
    }

    /**
     * <p>Attempts to resolve and connect to the {@link RequestHandler} appropriate for the {@link URI} of this Request.
     * An exception is thrown if this operation fails at any point. This method is exception-safe.</p>
     *
     * @param responseHandler The handler to pass the corresponding {@link Response} to.
     * @return The {@link ContentChannel} to write the Request content to.
     * @throws NullPointerException     If the {@link ResponseHandler} is null.
     * @throws BindingNotFoundException If the corresponding call to {@link Container#resolveHandler(Request)} returns
     *                                  null.
     */
    public ContentChannel connect(final ResponseHandler responseHandler) {
        try {
            Objects.requireNonNull(responseHandler, "responseHandler");
            RequestHandler requestHandler = container().resolveHandler(this);
            if (requestHandler == null) {
                throw new BindingNotFoundException(uri);
            }
            requestHandler = new ProxyRequestHandler(requestHandler);
            ContentChannel content = requestHandler.handleRequest(this, responseHandler);
            if (content == null) {
                throw new RequestDeniedException(this);
            }
            return content;
        }
        catch (Throwable t) {
            cancel();
            throw t;
        }
    }

    @Override
    protected void destroy() {
        if (parentReference != null) {
            parentReference.close();
        }
        if (container != null) {
            container.release();
        }
    }

}
