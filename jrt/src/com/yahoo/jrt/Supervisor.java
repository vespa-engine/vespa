// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 **/
public class Supervisor {

    private static final int SMALL_INPUT_BUFFER_SIZE = 20 * 1024;  // Large enough too hold the typical application buffersize of 17k.
    private static final int SMALL_OUTPUT_BUFFER_SIZE = 8 *1024;   // Suitable small buffer usage with many connections and little traffic.
    private final Transport         transport;
    private SessionHandler          sessionHandler = null;
    private final Object            methodMapLock = new Object();
    private final AtomicReference<HashMap<String, Method>> methodMap = new AtomicReference<>(new HashMap<>());
    private int                     maxInputBufferSize  = 0;
    private int                     maxOutputBufferSize = 0;

    /**
     * Create a new Supervisor based on the given {@link Transport}
     *
     * @param transport object performing low-level operations for
     * this Supervisor
     **/
    public Supervisor(Transport transport) {
        this.transport = transport;
        new MandatoryMethods(this);
    }

    /**
     * Will optimize buffers size for small memory footprint
     * Use this when you have many connections with very little traffic.
     **/
    public Supervisor useSmallBuffers() {
        setMaxInputBufferSize(SMALL_INPUT_BUFFER_SIZE);
        setMaxOutputBufferSize(SMALL_OUTPUT_BUFFER_SIZE);
        return this;
    }

    /**
     * Set maximum input buffer size. This value will only affect
     * connections that use a common input buffer when decoding
     * incoming packets. Note that this value is not an absolute
     * max. The buffer will still grow larger than this value if
     * needed to decode big packets. However, when the buffer becomes
     * larger than this value, it will be shrunk back when possible.
     *
     * @param bytes buffer size in bytes. 0 means unlimited.
     **/
    public void setMaxInputBufferSize(int bytes) {
        maxInputBufferSize = bytes;
    }

    /**
     * Set maximum output buffer size. This value will only affect
     * connections that use a common output buffer when encoding
     * outgoing packets. Note that this value is not an absolute
     * max. The buffer will still grow larger than this value if needed
     * to encode big packets. However, when the buffer becomes larger
     * than this value, it will be shrunk back when possible.
     *
     * @param bytes buffer size in bytes. 0 means unlimited.
     **/
    public void setMaxOutputBufferSize(int bytes) {
        maxOutputBufferSize = bytes;
    }

    /**
     * Obtain the method map for this Supervisor
     *
     * @return the method map
     **/
    HashMap<String, Method> methodMap() {
        return methodMap.getAcquire();
    }

    /**
     * Obtain the underlying Transport object.
     *
     * @return underlying Transport object
     **/
    public Transport transport() {
        return transport;
    }

    /**
     * Set the session handler for this Supervisor
     *
     * @param handler the session handler
     **/
    public void setSessionHandler(SessionHandler handler) {
        sessionHandler = handler;
    }

    /**
     * Add a method to the set of methods held by this Supervisor
     *
     * @param method the method to add
     **/
    public void addMethod(Method method) {
        synchronized (methodMapLock) {
            HashMap<String, Method> newMap = new HashMap<>(methodMap());
            newMap.put(method.name(), method);
            methodMap.setRelease(newMap);
        }
    }

    /**
     * Remove a method from the set of methods held by this
     * Supervisor. Use this if you know exactly which method to remove
     * and not only the name.
     *
     * @param method the method to remove
     **/
    public void removeMethod(Method method) {
        synchronized (methodMapLock) {
            HashMap<String, Method> newMap = new HashMap<>(methodMap());
            if (newMap.remove(method.name()) == method) {
                methodMap.setRelease(newMap);
            }
        }
    }

    /**
     * Connect to the given address. The new {@link Target} will be
     * associated with this Supervisor.
     *
     * @return Target representing our end of the connection
     * @param spec where to connect
     * @see #connect(com.yahoo.jrt.Spec, java.lang.Object)
     **/
    public Target connect(Spec spec) {
        return transport.connect(this, spec, null);
    }

    /**
     * Connect to the given address. The new {@link Target} will be
     * associated with this Supervisor and will have 'context' as
     * application context.
     *
     * @return Target representing our end of the connection
     * @param spec where to connect
     * @param context application context for the Target
     * @see Target#getContext
     **/
    public Target connect(Spec spec, Object context) {
        return transport.connect(this, spec, context);
    }

    /**
     * Listen to the given address.
     *
     * @return active object accepting new connections that will be
     * associated with this Supervisor
     * @param spec the address to listen to
     **/
    public Acceptor listen(Spec spec) throws ListenFailedException {
        return transport.listen(this, spec);
    }

    /**
     * Convenience method for connecting to a peer, invoking a method
     * and disconnecting.
     *
     * @param spec the address to connect to
     * @param req the invocation request
     * @param timeout request timeout in seconds
     **/
    public void invokeBatch(Spec spec, Request req, double timeout) {
        Target target = connect(spec);
        try {
            target.invokeSync(req, timeout);
        } finally {
            target.close();
        }
    }

    /**
     * This method is invoked when a new target is created
     *
     * @param target the target
     **/
    void sessionInit(Target target) {
        if (target instanceof Connection) {
            Connection conn = (Connection) target;
            conn.setMaxInputSize(maxInputBufferSize);
            conn.setMaxOutputSize(maxOutputBufferSize);
        }
        SessionHandler handler = sessionHandler;
        if (handler != null) {
            handler.handleSessionInit(target);
        }
    }

    /**
     * This method is invoked when a target establishes a connection
     * with its peer
     *
     * @param target the target
     **/
    void sessionLive(Target target) {
        SessionHandler handler = sessionHandler;
        if (handler != null) {
            handler.handleSessionLive(target);
        }
    }

    /**
     * This method is invoked when a target becomes invalid
     *
     * @param target the target
     **/
    void sessionDown(Target target) {
        SessionHandler handler = sessionHandler;
        if (handler != null) {
            handler.handleSessionDown(target);
        }
    }

    /**
     * This method is invoked when a target is invalid and no more
     * invocations are active
     *
     * @param target the target
     **/
    void sessionFini(Target target) {
        SessionHandler handler = sessionHandler;
        if (handler != null) {
            handler.handleSessionFini(target);
        }
    }

    /**
     * This method is invoked each time we write a packet. This method
     * is empty and only used for testing through sub-classing.
     *
     * @param info information about the written packet
     **/
    void writePacket(PacketInfo info) {}

    /**
     * This method is invoked each time we read a packet. This method
     * is empty and only used for testing through sub-classing.
     *
     * @param info information about the read packet
     **/
    void readPacket(PacketInfo info) {}

    /**
     * Handle a packet received on one of the connections associated
     * with this Supervisor. This method is invoked for all packets
     * not handled by a {@link ReplyHandler}
     *
     * @param conn where the packet came from
     * @param packet the packet
     **/
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
