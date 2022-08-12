// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;

import java.time.Duration;
import java.util.logging.Level;
import com.yahoo.text.Utf8Array;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * <p>Implements a target object that encapsulates the JRT connection
 * target. Instances of this class are returned by {@link RPCService}, and
 * cached by {@link RPCTargetPool}.</p>
 *
 * @author Simon Thoresen Hult
 */
public class RPCTarget implements RequestWaiter {

    private static final Logger log = Logger.getLogger(RPCTarget.class.getName());
    private final AtomicInteger ref = new AtomicInteger(1);
    private final String name;
    private final Target target;
    private boolean targetInvoked = false;
    private Version version = null;
    private List<VersionHandler> versionHandlers = new LinkedList<>();

    /**
     * <p>Constructs a new instance of this class.</p>
     *
     * @param spec The connection spec of this target.
     * @param orb  The jrt supervisor to use when connecting to target.
     */
    public RPCTarget(Spec spec, Supervisor orb) {
        this.name = spec.toString();
        this.target = orb.connect(spec);
    }

    /**
     * <p>Returns the encapsulated JRT target.</p>
     *
     * @return The target.
     */
    Target getJRTTarget() {
        return target;
    }

    /**
     * <p>This method is used for explicit reference counting targets to allow
     * reusing open connections. An instance of this class is constructed with a
     * single reference.</p>
     *
     * @see #subRef()
     */
    void addRef() {
        ref.incrementAndGet();
    }

    /**
     * <p>This method is used for explicit reference counting targets to allow
     * reusing open connections. When the reference count reaches 0, the
     * connection is closed.</p>
     *
     * @see #addRef()
     */
    void subRef() {
        if (ref.decrementAndGet() == 0) {
            target.close();
        }
    }

    /**
     * <p>Returns the current reference count of this target. If this ever
     * returns 0 it means the underlying connection is closed and invalid.</p>
     *
     * @return The number of references in use.
     */
    int getRefCount() {
        return ref.get();
    }

    /**
     * <p>Requests the version of this target be passed to the given {@link
     * VersionHandler}. If the version is available, the handler is called
     * synchronously; if not, the handler is called by the network thread once
     * the target responds to the version query.</p>
     *
     * @param timeout The timeout for the request in seconds.
     * @param handler The handler to be called once the version is available.
     */
    void resolveVersion(Duration timeout, VersionHandler handler) {
        boolean hasVersion = false;
        boolean shouldInvoke = false;
        boolean shouldLog = log.isLoggable(Level.FINE);
        synchronized (this) {
            if (version != null) {
                if (shouldLog) {
                    log.log(Level.FINE, "Version already available for target '" + name + "' (version " + version + ").");
                }
                hasVersion = true;
            } else {
                if (shouldLog) {
                    log.log(Level.FINE, "Registering version handler '" + handler + "' for target '" + name + "'.");
                }
                versionHandlers.add(handler);
                if (!targetInvoked) {
                    targetInvoked = true;
                    shouldInvoke = true;
                }
            }
        }
        if (hasVersion) {
            handler.handleVersion(version);
        } else if (shouldInvoke) {
            if (shouldLog) {
                log.log(Level.FINE, "Invoking mbus.getVersion() on target '" + name + "'");
            }
            Request req = new Request("mbus.getVersion");
            target.invokeAsync(req, timeout, this);
        }
    }

    @Override
    public void handleRequestDone(Request req) {
        List<VersionHandler> handlers;
        boolean shouldLog = log.isLoggable(Level.FINE);
        synchronized (this) {
            targetInvoked = false;
            if (req.checkReturnTypes("s")) {
                Utf8Array str = req.returnValues().get(0).asUtf8Array();
                try {
                    version = new Version(str);
                    if (shouldLog) {
                        log.log(Level.FINE, "Target '" + name + "' has version " + version + ".");
                    }
                } catch (IllegalArgumentException e) {
                    log.log(Level.WARNING, "Failed to parse '" + str + "' as version for target '" + name + "'.", e);
                }
            } else {
                log.log(Level.INFO, "Method mbus.getVersion() failed for target '" + name + "'; " +
                                       req.errorMessage());
            }
            handlers = versionHandlers;
            versionHandlers = new LinkedList<>();
        }
        for (VersionHandler handler : handlers) {
            handler.handleVersion(version);
        }
    }

    /**
     * <p>Declares a version handler used when resolving the version of a
     * target. An instance of this is passed to {@link
     * RPCTarget#resolveVersion(Duration, RPCTarget.VersionHandler)}, and invoked
     * either synchronously or asynchronously, depending on whether or not the
     * version is already available.</p>
     */
    public interface VersionHandler {

        /**
         * <p>This method is invoked once the version of the corresponding
         * {@link RPCTarget} becomes available. If a problem occured while
         * retrieving the version, this method is invoked with a null
         * argument.</p>
         *
         * @param ver The version of corresponding target, or null.
         */
        void handleVersion(Version ver);
    }
}
