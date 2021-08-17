// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Protocol;
import com.yahoo.text.Utf8Array;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * A bridge between the reusable, singleton RPC network, and the generational message bus which uses this.
 * The RPC network is required to be singular because of its unique resources, such as sockets.
 * This is complicated by the message bus potentially existing in different graph generation at any point in
 * time, with all copies potential users of the network interface, but where each message bus-registered session
 * should belong to a single message bus. This class solves these problems by tracking which sessions are
 * active in which message bus instance, and by (de)registering only when a session is registered to (no) message
 * bus instances.
 *
 * In time, this should allow us to get rid of the shared-this-and-that in the container, too ...
 *
 * @author jonmv
 */
public class NetworkMultiplexer implements NetworkOwner {

    private static final Logger log = Logger.getLogger(NetworkMultiplexer.class.getName());

    private final Network net;
    private final Queue<NetworkOwner> owners = new ConcurrentLinkedQueue<>();
    private final Map<String, Queue<NetworkOwner>> sessions = new ConcurrentHashMap<>();
    private final boolean shared;

    private NetworkMultiplexer(Network net, boolean shared) {
        net.attach(this);
        this.net = net;
        this.shared = shared;
    }

    /** Returns a network multiplexer which will be shared between several {@link NetworkOwner}s. */
    public static NetworkMultiplexer shared(Network net) {
        return new NetworkMultiplexer(net, true);
    }

    /** Returns a network multiplexer with a single {@link NetworkOwner}, which shuts down when this owner detaches. */
    public static NetworkMultiplexer dedicated(Network net) {
        return new NetworkMultiplexer(net, false);
    }

    public void registerSession(String session, NetworkOwner owner, boolean broadcast) {
        sessions.compute(session, (name, owners) -> {
            if (owners == null) {
                owners = new ConcurrentLinkedQueue<>();
                if (broadcast)
                    net.registerSession(session);
            }
            else if (owners.contains(owner))
                throw new IllegalArgumentException("Session '" + session + "' with owner '" + owner + "' already registered with this");

            owners.add(owner);
            return owners;
        });
    }

    public void unregisterSession(String session, NetworkOwner owner, boolean broadcast) {
        sessions.compute(session, (name, owners) -> {
            if (owners == null || ! owners.remove(owner))
                throw new IllegalArgumentException("Session '" + session + "' not registered with owner '" + owner + "'");

            if (owners.isEmpty()) {
                if (broadcast)
                    net.unregisterSession(session);
                return null;
            }
            return owners;
        });
    }

    @Override
    public Protocol getProtocol(Utf8Array name) {
        // Should ideally couple this to the actual receiver ...
        Protocol protocol = null;
        for (NetworkOwner owner : owners)
            protocol = owner.getProtocol(name) == null ? protocol : owner.getProtocol(name);

        return protocol;
    }

    @Override
    public void deliverMessage(Message message, String session) {
        // Send to first owner which has registered this session, or fall back to first attached owner (for rejection).
        NetworkOwner owner = sessions.getOrDefault(session, owners).peek();
        if (owner == null) { // Should not happen.
            log.warning(this + " received message '" + message + "' with no owners attached");
            message.discard();
        }
        else
            owner.deliverMessage(message, session);
    }

    public void attach(NetworkOwner owner) {
        if (owners.contains(owner))
            throw new IllegalArgumentException(owner + " is already attached to this");

        owners.add(owner);
    }

    public void detach(NetworkOwner owner) {
        if ( ! owners.remove(owner))
            throw new IllegalArgumentException(owner + " not attached to this");

        if ( ! shared && owners.isEmpty())
            net.shutdown();
    }

    public void destroy() {
        if ( ! shared)
            throw new UnsupportedOperationException("Destroy called on a dedicated multiplexer; " +
                                                    "this automatically shuts down when detached from");

        if ( ! owners.isEmpty())
            log.warning("NetworkMultiplexer destroyed before all owners detached: " + this);

        net.shutdown();
    }

    public Network net() {
        return net;
    }

    @Override
    public String toString() {
        return "NetworkMultiplexer{" +
               "net=" + net +
               ", owners=" + owners +
               ", sessions=" + sessions +
               ", shared=" + shared +
               '}';
    }

}