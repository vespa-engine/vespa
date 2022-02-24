// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * Interface used to handle the lifetime of a {@link Target}. The word
 * session is used to denote all the RPC activity across a single
 * {@link Target} during its lifetime. This interface gives the
 * application information about when different {@link Target} objects
 * enter different stages in their lifetime. Combined with the ability
 * to bind application specific data to a {@link Target} with the
 * {@link Target#setContext} method, this enables method invocations
 * in the same session to share state information. Usage of this
 * interface is optional. It is typically useful for server
 * applications needing state to be shared between RPC method
 * invocation on a session. Each {@link Supervisor} can only have a
 * single session handler. Use the {@link Supervisor#setSessionHandler
 * Supervisor.setSessionHandler} method to set the session
 * handler. The different callbacks may be called from several
 * different threads, but for a single target there will be no
 * overlapping of session callbacks, and the order will always be the
 * same; init, live (not always called), down, fini.
 **/
public interface SessionHandler {

    /**
     * Invoked when a new {@link Target} is created. This is a nice
     * place to initialize and attach application context to the
     * {@link Target}.
     *
     * @param target the target
     **/
    public void handleSessionInit(Target target);

    /**
     * Invoked when a connection is established with the peer. Note
     * that if a connection could not be established with the peer,
     * this method is never invoked.
     *
     * @param target the target
     **/
    public void handleSessionLive(Target target);

    /**
     * Invoked when the target becomes invalid. This is typically
     * caused by the network connection with the peer going down. Note
     * that this method is invoked also when a connection with the
     * peer could not be established at all.
     *
     * @param target the target
     **/
    public void handleSessionDown(Target target);

    /**
     * Invoked when the target is invalid and no more RPC invocations
     * are active on our side of this target (invoked from the other
     * side; we being the server). If you need to perform cleanup
     * related to the application data associated with the target, you
     * should wait until this method is invoked, to avoid cleaning up
     * the {@link Target} application context under the feet of active
     * invocations.
     *
     * @param target the target
     **/
    public void handleSessionFini(Target target);
}
