// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.routing.RoutingNode;

import java.util.List;

/**
 * This interface separates the low-level network implementation from the rest of messagebus. The methods defined in
 * this interface are intended to be invoked by MessageBus and not by the application.
 *
 * @author havardpe
 */
public interface Network {

    /**
     * Waits for at most the given number of seconds for all dependencies to become ready.
     *
     * @param seconds the timeout
     * @return true if ready
     */
    boolean waitUntilReady(double seconds);

    /**
     * Attach the network layer to the given owner.
     *
     * @param owner owner of the network
     */
    void attach(NetworkOwner owner);

    /**
     * Register a session name with the network layer. This will make the session visible to other nodes.
     *
     * @param session the session name
     */
    void registerSession(String session);

    /**
     * Unregister a session name with the network layer. This will make the session unavailable for other nodes.
     *
     * @param session session name
     */
    void unregisterSession(String session);

    /**
     * Resolves the service address of the recipient referenced by the given routing node. If a recipient can not be
     * resolved, this method tags the node with an error. If this method succeeds, you need to invoke {@link
     * #freeServiceAddress(RoutingNode)} once you are done with the service address.
     *
     * @param recipient the node whose service address to allocate
     * @return true if a service address was allocated
     */
    boolean allocServiceAddress(RoutingNode recipient);

    /**
     * Frees the service address from the given routing node. This allows the network layer to track and close
     * connections as required.
     *
     * @param recipient the node whose service address to free
     */
    void freeServiceAddress(RoutingNode recipient);

    /**
     * Send a message to the given recipients. A {@link RoutingNode} contains all the necessary context for sending.
     *
     * @param msg        the message to send
     * @param recipients a list of routing leaf nodes resolved for the message
     */
    void send(Message msg, List<RoutingNode> recipients);

    /**
     * Synchronize with internal threads. This method will handshake with all internal threads. This has the implicit
     * effect of waiting for all active callbacks. Note that this method should never be invoked from a callback since
     * that would make the thread wait for itself... forever. This method is typically used to untangle during session
     * shutdown.
     */
    void sync();

    /** Shuts down the network. This is a blocking call that waits for all scheduled tasks to complete. */
    void shutdown();

    /**
     * Returns a string that represents the connection specs of this network.
     * It is in not a complete address since it know nothing of the sessions that run on it.
     */
    String getConnectionSpec();

    /** Returns a reference to a name server mirror. */
    IMirror getMirror();

}
