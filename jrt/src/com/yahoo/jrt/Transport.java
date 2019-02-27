// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
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

    private static final int OPEN    = 1;
    private static final int CLOSING = 2;
    private static final int CLOSED  = 3;

    private class Run implements Runnable {
        public void run() {
            try {
                Transport.this.run();
            } catch (Throwable problem) {
                handleFailure(problem, Transport.this);
            }
        }
    }

    private class AddConnectionCmd implements Runnable {
        private Connection conn;
        AddConnectionCmd(Connection conn) { this.conn = conn; }
        public void run() { handleAddConnection(conn); }
    }

    private class CloseConnectionCmd implements Runnable {
        private Connection conn;
        CloseConnectionCmd(Connection conn) { this.conn = conn; }
        public void run() { handleCloseConnection(conn); }
    }

    private class EnableWriteCmd implements Runnable {
        private Connection conn;
        EnableWriteCmd(Connection conn) { this.conn = conn; }
        public void run() { handleEnableWrite(conn); }
    }

    private class SyncCmd implements Runnable {
        boolean done = false;
        public synchronized void waitDone() {
            while (!done) {
                try { wait(); } catch (InterruptedException e) {}
            }
        }
        public synchronized void run() {
            done = true;
            notify();
        }
    }

    private static Logger log = Logger.getLogger(Transport.class.getName());

    private FatalErrorHandler fatalHandler; // NB: this must be set first
    private CryptoEngine      cryptoEngine;
    private Thread            thread;
    private Queue             queue;
    private Queue             myQueue;
    private Connector         connector;
    private Closer            closer;
    private Scheduler         scheduler;
    private int               state;
    private Selector          selector;
    private final TransportMetrics metrics = TransportMetrics.getInstance();

    private void handleAddConnection(Connection conn) {
        if (conn.isClosed()) {
            if (conn.hasSocket()) {
                closer.closeLater(conn);
            }
            return;
        }
        if (!conn.init(selector)) {
            handleCloseConnection(conn);
        }
    }

    private void handleCloseConnection(Connection conn) {
        if (conn.isClosed()) {
            return;
        }
        conn.fini();
        if (conn.hasSocket()) {
            closer.closeLater(conn);
        }
    }

    private void handleEnableWrite(Connection conn) {
        if (conn.isClosed()) {
            return;
        }
        conn.enableWrite();
    }

    private boolean postCommand(Runnable cmd) {
        boolean wakeup;
        synchronized (this) {
            if (state == CLOSED) {
                return false;
            }
            wakeup = queue.isEmpty();
            queue.enqueue(cmd);
        }
        if (wakeup) {
            selector.wakeup();
        }
        return true;
    }

    private void handleEvents() {
        synchronized (this) {
            queue.flush(myQueue);
        }
        while (!myQueue.isEmpty()) {
            ((Runnable)myQueue.dequeue()).run();
        }
    }

    private boolean handleIOEvents(Connection conn,
                                   SelectionKey key) {
        if (conn.isClosed()) {
            return true;
        }
        if (key.isReadable()) {
            try {
                conn.handleReadEvent();
            } catch (IOException e) {
                conn.setLostReason(e);
                return false;
            }
        }
        if (key.isWritable()) {
            try {
                conn.handleWriteEvent();
            } catch (IOException e) {
                conn.setLostReason(e);
                return false;
            }
        }
        return true;
    }

    /**
     * Create a new Transport object with the given fatal error
     * handler and CryptoEngine. If a fatal error occurs when no fatal
     * error handler is registered, the default action is to log the
     * error and exit with exit code 1.
     *
     * @param fatalHandler fatal error handler
     * @param cryptoEngine crypto engine to use
     **/
    public Transport(FatalErrorHandler fatalHandler, CryptoEngine cryptoEngine) {
        synchronized (this) {
            this.fatalHandler = fatalHandler; // NB: this must be set first
        }
        this.cryptoEngine = cryptoEngine;
        thread    = new Thread(new Run(), "<jrt-transport>");
        queue     = new Queue();
        myQueue   = new Queue();
        connector = new Connector(this);
        closer    = new Closer(this);
        scheduler = new Scheduler(System.currentTimeMillis());
        state     = OPEN;
        try {
            selector = Selector.open();
        } catch (Exception e) {
            throw new Error("Could not open transport selector", e);
        }
        thread.setDaemon(true);
        thread.start();
    }
    public Transport(CryptoEngine cryptoEngine) { this(null, cryptoEngine); }
    public Transport(FatalErrorHandler fatalHandler) { this(fatalHandler, CryptoEngine.createDefault()); }
    public Transport() { this(null, CryptoEngine.createDefault()); }

    /**
     * Use the underlying CryptoEngine to create a CryptoSocket.
     *
     * @return CryptoSocket handling appropriate encryption
     * @param channel low-level socket channel to be wrapped by the CryptoSocket
     * @param isServer flag indicating which end of the connection we are
     **/
    CryptoSocket createCryptoSocket(SocketChannel channel, boolean isServer) {
        return cryptoEngine.createCryptoSocket(channel, isServer);
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
     * @param sync perform a synchronous connect in the calling thread
     *             if this flag is set
     */
    Connection connect(Supervisor owner, Spec spec, Object context, boolean sync) {
        Connection conn = new Connection(this, owner, spec, context);
        if (sync) {
            addConnection(conn.connect());
        } else {
            connector.connectLater(conn);
        }
        return conn;
    }

    /**
     * Add a connection to the set of connections handled by this
     * Transport. Invoked by the {@link Connector} class.
     *
     * @param conn the connection to add
     **/
    void addConnection(Connection conn) {
        if (!postCommand(new AddConnectionCmd(conn))) {
            perform(new CloseConnectionCmd(conn));
        }
    }

    /**
     * Request an asynchronous close of a connection.
     *
     * @param conn the connection to close
     **/
    void closeConnection(Connection conn) {
        postCommand(new CloseConnectionCmd(conn));
    }

    /**
     * Request an asynchronous enabling of write events for a
     * connection.
     *
     * @param conn the connection to enable write events for
     **/
    void enableWrite(Connection conn) {
        if (Thread.currentThread() == thread) {
            handleEnableWrite(conn);
        } else {
            postCommand(new EnableWriteCmd(conn));
        }
    }

    /**
     * Create a {@link Task} that can be scheduled for execution in
     * the transport thread.
     *
     * @return the newly created Task
     * @param cmd what to run when the task is executed
     **/
    public Task createTask(Runnable cmd) {
        return new Task(scheduler, cmd);
    }

    /**
     * Perform the given command in such a way that it does not run
     * concurrently with the transport thread or other commands
     * performed by invoking this method. This method will continue to
     * work even after the transport thread has been shut down.
     *
     * @param cmd the command to perform
     **/
    public void perform(Runnable cmd) {
        if (Thread.currentThread() == thread) {
            cmd.run();
            return;
        }
        if (!postCommand(cmd)) {
            join();
            synchronized (thread) {
                cmd.run();
            }
        }
    }

    /**
     * Synchronize with the transport thread. This method will block
     * until all commands issued before this method was invoked has
     * completed. If the transport thread has been shut down (or is in
     * the progress of being shut down) this method will instead wait
     * for the transport thread to complete, since no more commands
     * will be performed, and waiting would be forever. Invoking this
     * method from the transport thread is not a good idea.
     *
     * @return this object, to enable chaining
     **/
    public Transport sync() {
        SyncCmd cmd = new SyncCmd();
        if (postCommand(cmd)) {
            cmd.waitDone();
        } else {
            join();
        }
        return this;
    }

    private void run() {
        while (state == OPEN) {

            // perform I/O selection
            try {
                selector.select(100);
            } catch (IOException e) {
                log.log(Level.WARNING, "error during select", e);
            }

            // handle internal events
            handleEvents();

            // handle I/O events
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                Connection conn = (Connection) key.attachment();
                keys.remove();
                if (!handleIOEvents(conn, key)) {
                    handleCloseConnection(conn);
                }
            }

            // check scheduled tasks
            scheduler.checkTasks(System.currentTimeMillis());
        }
        connector.shutdown().waitDone();
        synchronized (this) {
            state = CLOSED;
        }
        handleEvents();
        Iterator<SelectionKey> keys = selector.keys().iterator();
        while (keys.hasNext()) {
            SelectionKey key = keys.next();
            Connection conn = (Connection) key.attachment();
            handleCloseConnection(conn);
        }
        try { selector.close(); } catch (Exception e) {}
        closer.shutdown().join();
        connector.exit().join();
        try { cryptoEngine.close(); } catch (Exception e) {}
    }

    /**
     * Initiate controlled shutdown of the transport thread.
     *
     * @return this object, to enable chaining with join
     **/
    public Transport shutdown() {
        synchronized (this) {
            if (state == OPEN) {
                state = CLOSING;
                selector.wakeup();
            }
        }
        return this;
    }

    /**
     * Wait for the transport thread to finish.
     **/
    public void join() {
        while (true) {
            try {
                thread.join();
                return;
            } catch (InterruptedException e) {}
        }
    }

    public TransportMetrics metrics() {
        return metrics;
    }
}
