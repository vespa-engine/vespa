// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.util.Optional;

/**
 * A Target represents a connection endpoint with RPC
 * capabilities. Each such connection has a client and a server
 * side. The client side is the one initiating the connection. RPC
 * requests may be invoked across the connection from both the client
 * and the server side.
 **/
public abstract class Target {

    private Object context;

    /**
     * Create a Target with the given application context.
     *
     * @param context application context
     **/
    Target(Object context) {
        this.context = context;
    }

    /**
     * Create a Target without any application context.
     **/
    Target() {
        this(null);
    }

    /**
     * Set the application context associated with this target.
     *
     * @param context application context
     **/
    public void setContext(Object context) {
        this.context = context;
    }

    /**
     * Obtain the application context associated with this target.
     *
     * @return application context
     **/
    public Object getContext() {
        return context;
    }

    /**
     * Check if this target is still valid for invocations.
     *
     * @return true if this target is still valid
     **/
    public abstract boolean isValid();

    /**
     * Obtain the low-level reason behind losing the connection for
     * which this target is an endpoint. If the target is still valid
     * or if the target became invalid because it was closed, this
     * method will return null. In other cases this method may or may
     * not return an exception indicating why the connection was
     * lost. Also, if an exception is returned, its nature may vary
     * based on implementation details across platforms.
     *
     * @return exception causing connection loss or null
     **/
    public Exception getConnectionLostReason() { return null; }

    /**
     * @return the security context associated with this target, or empty if no connection or is insecure.
     */
    public abstract Optional<SecurityContext> getSecurityContext();

    /**
     * Check if this target represents the client side of a
     * connection.
     *
     * @return true if this is a client-side target
     **/
    public abstract boolean isClient();

    /**
     * Check if this target represents the server side of a
     * connection.
     *
     * @return true if this is a server-side target
     **/
    public abstract boolean isServer();

    /**
     * Invoke a request on this target and wait for it to return.
     *
     * @param req the request
     * @param timeout timeout in seconds
     **/
    public abstract void invokeSync(Request req, double timeout);

    /**
     * Invoke a request on this target and let the completion be
     * signalled with a callback.
     *
     * @param req the request
     * @param timeout timeout in seconds
     * @param waiter callback handler
     **/
    public abstract void invokeAsync(Request req, double timeout,
                                     RequestWaiter waiter);

    /**
     * Invoke a request on this target, but ignore the return
     * value(s). The success or failure of the invocation is also
     * ignored. However, the return value gives a little hint by
     * indicating whether the invocation has been attempted at all.
     *
     * @return false if the invocation was not attempted due to the
     *         target being invalid
     * @param req the request
     **/
    public abstract boolean invokeVoid(Request req);

    /**
     * Add a watcher to this target. A watcher is notified if the
     * target becomes invalid. If the target is already invalid when
     * this method is invoked, no operation is performed and false is
     * returned. Multiple adds of the same watcher has no additional
     * effect.
     *
     * @return true if the add operation was performed
     * @param watcher the watcher to be added
     **/
    public abstract boolean addWatcher(TargetWatcher watcher);

    /**
     * Remove a watcher from this target. If the target is already
     * invalid when this method is invoked, no operation is performed
     * and false is returned. Multiple removes of the same watcher has
     * no additional effect.
     *
     * @return true if the remove operation was performed
     * @param watcher the watcher to be removed
     * @see #addWatcher
     **/
    public abstract boolean removeWatcher(TargetWatcher watcher);

    /**
     * Close this target. Note that the close operation is
     * asynchronous. If you need to wait for the target to become
     * invalid, use the {@link Transport#sync Transport.sync} method.
     **/
    public abstract void close();
}
