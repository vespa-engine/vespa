// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Supervisor keeps a method repository and handles dispatching of
 * incoming invocation requests. Each end-point of a connection is
 * represented by a {@link Target} object and each {@link Target} is
 * associated with a single Supervisor that handles the invocation
 * requests obtained from that {@link Target}. Note that RPC
 * invocations can be performed both ways across a connection, so even
 * the client side of a connection has RPC server capabilities.
 */
public class Supervisor {

    private final Transport         transport;
    private final Object            methodMapLock = new Object();
    private final AtomicReference<HashMap<String, Method>> methodMap = new AtomicReference<>(new HashMap<>());
    private int                     maxInputBufferSize  = 64*1024;
    private int                     maxOutputBufferSize = 64*1024;
    private boolean                 dropEmptyBuffers = false;

    /**
     * Creates a new Supervisor based on the given {@link Transport}
     *
     * @param transport object performing low-level operations for this Supervisor
     */
    public Supervisor(Transport transport) {
        this.transport = transport;
        new MandatoryMethods(this);
    }

    /**
     * Drops empty buffers. This will reduce memory footprint for idle
     * connections at the cost of extra allocations when buffer space
     * is needed again.
     *
     * @param value true means drop empty buffers
     */
    public Supervisor setDropEmptyBuffers(boolean value) {
        dropEmptyBuffers = value;
        return this;
    }
    boolean getDropEmptyBuffers() { return dropEmptyBuffers; }

    /**
     * Sets maximum input buffer size. This value will only affect
     * connections that use a common input buffer when decoding
     * incoming packets. Note that this value is not an absolute
     * max. The buffer will still grow larger than this value if
     * needed to decode big packets. However, when the buffer becomes
     * larger than this value, it will be shrunk back when possible.
     *
     * @param bytes buffer size in bytes. 0 means unlimited.
     */
    public void setMaxInputBufferSize(int bytes) {
        maxInputBufferSize = bytes;
    }
    int getMaxInputBufferSize() { return maxInputBufferSize; }

    /**
     * Sets maximum output buffer size. This value will only affect
     * connections that use a common output buffer when encoding
     * outgoing packets. Note that this value is not an absolute
     * max. The buffer will still grow larger than this value if needed
     * to encode big packets. However, when the buffer becomes larger
     * than this value, it will be shrunk back when possible.
     *
     * @param bytes buffer size in bytes. 0 means unlimited.
     */
    public void setMaxOutputBufferSize(int bytes) {
        maxOutputBufferSize = bytes;
    }
    int getMaxOutputBufferSize() { return maxOutputBufferSize; }

    /**
     * Obtains the method map for this Supervisor
     *
     * @return the method map
     */
    HashMap<String, Method> methodMap() {
        return methodMap.getAcquire();
    }

    /**
     * Obtains the underlying Transport object.
     *
     * @return underlying Transport object
     */
    public Transport transport() {
        return transport;
    }

    /**
     * Adds a method to the set of methods held by this Supervisor
     *
     * @param method the method to add
     */
    public void addMethod(Method method) {
        synchronized (methodMapLock) {
            HashMap<String, Method> newMap = new HashMap<>(methodMap());
            newMap.put(method.name(), method);
            methodMap.setRelease(newMap);
        }
    }

    /**
     * Removes a method from the set of methods held by this
     * Supervisor. Use this if you know exactly which method to remove
     * and not only the name.
     *
     * @param method the method to remove
     */
    public void removeMethod(Method method) {
        synchronized (methodMapLock) {
            HashMap<String, Method> newMap = new HashMap<>(methodMap());
            if (newMap.remove(method.name()) == method) {
                methodMap.setRelease(newMap);
            }
        }
    }

    /**
     * Connects to the given address. The new {@link Target} will be
     * associated with this Supervisor.
     *
     * @return Target representing our end of the connection
     * @param spec where to connect
     * @see #connect(com.yahoo.jrt.Spec, java.lang.Object)
     */
    public Target connect(Spec spec) {
        return transport.connect(this, spec, null);
    }

    /**
     * Connects to the given address. The new {@link Target} will be
     * associated with this Supervisor and will have 'context' as
     * application context.
     *
     * @return Target representing our end of the connection
     * @param spec where to connect
     * @param context application context for the Target
     * @see Target#getContext
     */
    public Target connect(Spec spec, Object context) {
        return transport.connect(this, spec, context);
    }

    /**
     * Listens to the given address.
     *
     * @return active object accepting new connections that will be
     * associated with this Supervisor
     * @param spec the address to listen to
     */
    public Acceptor listen(Spec spec) throws ListenFailedException {
        return transport.listen(this, spec);
    }

    /**
     * This method is invoked each time we write a packet. This method
     * is empty and only used for testing through sub-classing.
     *
     * @param info information about the written packet
     */
    void writePacket(PacketInfo info) {}

    /**
     * This method is invoked each time we read a packet. This method
     * is empty and only used for testing through sub-classing.
     *
     * @param info information about the read packet
     */
    void readPacket(PacketInfo info) {}

    /**
     * Handles a packet received on one of the connections associated
     * with this Supervisor. This method is invoked for all packets
     * not handled by a {@link ReplyHandler}
     *
     * @param conn where the packet came from
     * @param packet the packet
     */
    void handlePacket(Connection conn, Packet packet) {
        if (packet.packetCode() != Packet.PCODE_REQUEST) {
            return;
        }
        RequestPacket rp = (RequestPacket) packet;
        Request req = new Request(rp.methodName(), rp.parameters());
        Method method = methodMap().get(req.methodName());
        new InvocationServer(conn, req, method,
                             packet.requestId(),
                             packet.noReply()).invoke();
    }

}
