// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Transport class is the core needed to make your {@link
 * Supervisor} tick. It implements the reactor pattern to perform
 * multiplexed network IO, handles scheduled tasks and keeps track of
 * some additional helper threads. A single Transport object can back
 * multiple {@link Supervisor} objects.
 **/
public class Transport {

    private static final Logger log = Logger.getLogger(Transport.class.getName());

    private final String name;
    private final FatalErrorHandler fatalHandler; // NB: this must be set first
    private final CryptoEngine      cryptoEngine;
    private final Connector         connector;
    private final Worker            worker;
    private final AtomicInteger     runCnt;
    private final boolean tcpNoDelay;
    private final int eventsBeforeWakeup;

    private final TransportMetrics metrics = TransportMetrics.getInstance();
    private final ArrayList<TransportThread> threads = new ArrayList<>();
    private final Random rnd = new Random();

    /**
     * Create a new Transport object with the given fatal error
     * handler and CryptoEngine. If a fatal error occurs when no fatal
     * error handler is registered, the default action is to log the
     * error and exit with exit code 1.
     *
     * @param name used for identifying threads
     * @param fatalHandler fatal error handler
     * @param cryptoEngine crypto engine to use
     * @param numThreads number of {@link TransportThread}s.
     * @param eventsBeforeWakeup number write events in Q before waking thread up
     **/
    public Transport(String name, FatalErrorHandler fatalHandler, CryptoEngine cryptoEngine, int numThreads, boolean tcpNoDelay, int eventsBeforeWakeup) {
        this.name = name;
        this.fatalHandler = fatalHandler; // NB: this must be set first
        this.cryptoEngine = cryptoEngine;
        this.tcpNoDelay = tcpNoDelay;
        this.eventsBeforeWakeup = Math.max(1, eventsBeforeWakeup);
        connector = new Connector();
        worker = new Worker(this);
        runCnt = new AtomicInteger(numThreads);
        for (int i = 0; i < numThreads; ++i) {
            threads.add(new TransportThread(this, i));
        }
    }
    public Transport(String name, CryptoEngine cryptoEngine, int numThreads, int eventsBeforeWakeup) {
        this(name, null, cryptoEngine, numThreads, true, eventsBeforeWakeup);
    }
    public Transport(String name, CryptoEngine cryptoEngine, int numThreads) {
        this(name, null, cryptoEngine, numThreads, true, 1);
    }
    public Transport(String name, int numThreads, int eventsBeforeWakeup) {
        this(name, null, CryptoEngine.createDefault(), numThreads, true, eventsBeforeWakeup);
    }
    public Transport(String name, int numThreads, boolean tcpNoDelay, int eventsBeforeWakeup) {
        this(name, null, CryptoEngine.createDefault(), numThreads, tcpNoDelay, eventsBeforeWakeup); }
    public Transport(String name, int numThreads) {
        this(name, null, CryptoEngine.createDefault(), numThreads, true, 1);
    }
    public Transport(String name) {
        this(name, null, CryptoEngine.createDefault(), 1, true, 1);
    }
    // Only for testing
    public Transport() { this("default"); }

    /**
     * Select a random transport thread
     *
     * @return a random transport thread
     **/
    public TransportThread selectThread() {
        return threads.get(rnd.nextInt(threads.size()));
    }

    boolean getTcpNoDelay() { return tcpNoDelay; }
    int getEventsBeforeWakeup() { return eventsBeforeWakeup; }

    String getName() { return name; }

    /**
     * Use the underlying CryptoEngine to create a CryptoSocket for
     * the client side of a connection.
     *
     * @return CryptoSocket handling appropriate encryption
     * @param channel low-level socket channel to be wrapped by the CryptoSocket
     * @param spec who we are connecting to, for hostname validation
     **/
    CryptoSocket createClientCryptoSocket(SocketChannel channel, Spec spec) {
        return cryptoEngine.createClientCryptoSocket(channel, spec);
    }

    /**
     * Use the underlying CryptoEngine to create a CryptoSocket for
     * the server side of a connection.
     *
     * @return CryptoSocket handling appropriate encryption
     * @param channel low-level socket channel to be wrapped by the CryptoSocket
     **/
    CryptoSocket createServerCryptoSocket(SocketChannel channel) {
        return cryptoEngine.createServerCryptoSocket(channel);
    }

    /**
     * Proxy method used to dispatch fatal errors to the fatal error
     * handler. If no handler is registered, the default action is to
     * log the error and halt the Java VM.
     *
     * @param problem the throwable causing the failure
     * @param context the object owning the crashing thread
     **/
    void handleFailure(Throwable problem, Object context) {
        if (fatalHandler != null) {
            fatalHandler.handleFailure(problem, context);
            return;
        }
        try {
            log.log(Level.SEVERE, "fatal error in " + context, problem);
        } catch (Throwable ignore) {}
        Runtime.getRuntime().halt(1);
    }

    /**
     * Listen to the given address. This method is called by a {@link
     * Supervisor} object.
     *
     * @return active object accepting new connections
     * @param owner the one calling this method
     * @param spec the address to listen to
     **/
    Acceptor listen(Supervisor owner, Spec spec) throws ListenFailedException {
        return new Acceptor(this, owner, spec);
    }

    /**
     * Connect to the given address. This method is called by a {@link
     * Supervisor} object.
     *
     * @return the new connection
     * @param owner the one calling this method
     * @param spec the address to connect to
     * @param context application context for the new connection
     */
    Connection connect(Supervisor owner, Spec spec, Object context) {
        Connection conn = new Connection(selectThread(), owner, spec, context, getTcpNoDelay());
        connector.connectLater(conn);
        return conn;
    }

    void closeLater(Connection c) {
        worker.closeLater(c);
    }

    /**
     * Request that {@link Connection#doHandshakeWork()} be called (in any thread)
     * followed by a call to {@link Connection#handleHandshakeWorkDone()} from the transport thread.
     *
     * @param conn the connection needing handshake work
     */
    void doHandshakeWork(Connection conn) {
        worker.doHandshakeWork(conn);
    }

    /**
     * Synchronize with all transport threads. This method will block
     * until all commands issued before this method was invoked has
     * completed. If a transport thread has been shut down (or is in
     * the progress of being shut down) this method will instead wait
     * for the transport thread to complete, since no more commands
     * will be performed, and waiting would be forever. Invoking this
     * method from a transport thread is not a good idea.
     *
     * @return this object, to enable chaining
     **/
    public Transport sync() {
        for (TransportThread thread: threads) {
            thread.sync();
        }
        return this;
    }

    /**
     * Initiate controlled shutdown of all transport threads.
     *
     * @return this object, to enable chaining with join
     **/
    public Transport shutdown() {
        connector.close();
        for (TransportThread thread: threads) {
            thread.shutdown();
        }
        return this;
    }

    /**
     * Wait for all transport threads to finish.
     **/
    public void join() {
        for (TransportThread thread: threads) {
            thread.join();
        }
    }

    void notifyDone(TransportThread self) {
        if (runCnt.decrementAndGet() == 0) {
            worker.shutdown().join();
            try { cryptoEngine.close(); } catch (Exception e) {}
        }
    }

    public TransportMetrics metrics() {
        return metrics;
    }
}
