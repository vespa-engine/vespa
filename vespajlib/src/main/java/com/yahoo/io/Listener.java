// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;


import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.net.InetSocketAddress;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;


/**
 * A basic Reactor implementation using NIO.
 *
 * @author <a href="mailto:travisb@yahoo-inc.com">Bob Travis</a>
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 *
 */
public class Listener extends Thread {
    private static Logger log = Logger.getLogger(Listener.class.getName());
    private Selector selector;
    Map<Integer, Acceptor> acceptors = new HashMap<>();
    Map<ServerSocketChannel, ConnectionFactory> factories = new IdentityHashMap<>();

    private FatalErrorHandler fatalErrorHandler;

    private List<SelectLoopHook> selectLoopPreHooks;
    private List<SelectLoopHook> selectLoopPostHooks;

    final private LinkedList<Connection> newConnections = new LinkedList<>();

    // queue of SelectionKeys that need to be updated
    final private LinkedList<UpdateInterest> modifyInterestOpsQueue = new LinkedList<>();

    public Listener(String name) {
        super("Listener-" + name);

        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.fine(name + " listener created " + this);
    }

    /**
     * Register a handler for fatal errors.
     *
     * @param f The FatalErrorHandler instance to be registered
     */
    public synchronized void setFatalErrorHandler(FatalErrorHandler f) {
        fatalErrorHandler = f;
    }

    /**
     * Add pre-select loop hook.  Not threadsafe so please do this
     * during initial setup before you start the listener.
     */
    public void addSelectLoopPreHook(SelectLoopHook hook) {
        if (selectLoopPreHooks == null) {
            selectLoopPreHooks = new ArrayList<>(5);
        }
        selectLoopPreHooks.add(hook);
    }

    /**
     * Add pre-select loop hook.  Not threadsafe so please do this
     * during initial setup before you start the listener.
     */
    public void addSelectLoopPostHook(SelectLoopHook hook) {
        if (selectLoopPostHooks == null) {
            selectLoopPostHooks = new ArrayList<>(5);
        }
        selectLoopPostHooks.add(hook);
    }

    /**
     * Run all the select loop pre hooks
     */
    private void runSelectLoopPreHooks() {
        if (selectLoopPreHooks == null) {
            return;
        }

        for (SelectLoopHook hook : selectLoopPreHooks) {
            hook.selectLoopHook(true);
        }
    }

    /**
     * Run all the select loop post hooks
     */
    private void runSelectLoopPostHooks() {
        if (selectLoopPostHooks == null) {
            return;
        }

        for (SelectLoopHook hook : selectLoopPostHooks) {
            hook.selectLoopHook(false);
        }
    }

    /**
     * Add a listening port and create an Acceptor thread which accepts
     * new connections on this port.
     *
     * @param factory The connection factory for new connections
     *                on this port
     * @param port The port we are going to listen to.
     */
    public synchronized void listen(ConnectionFactory factory, int port)
        throws IOException {
        // make sure we have only one acceptor per listen port
        if (acceptors.containsKey(port)) {
            log.warning("Already listening to port=" + port);
            return;
        }

        Acceptor a = new Acceptor(this, factory, port);

        // inherit the fatal error handling of listener
        if (fatalErrorHandler != null) {
            a.setFatalErrorHandler(fatalErrorHandler);
        }

        a.listen().start();
        acceptors.put(port, a);
    }

    /**
     * Add a listening port without creating a separate acceptor
     * thread.
     *
     * @param factory The connection factory for new connections
     *                on this port
     * @param port The port we are going to listen to.
     */
    public synchronized void listenNoAcceptor(ConnectionFactory factory, int port)
        throws IOException {
        ServerSocketChannel s = ServerSocketChannel.open();

        s.configureBlocking(false);
        s.socket().setReuseAddress(true);
        s.socket().bind(new InetSocketAddress(port)); // use non-specific IP
        String host = s.socket().getInetAddress().getHostName();

        factories.put(s, factory);
        s.register(selector, SelectionKey.OP_ACCEPT);
        log.fine("listener " + host + ":" + port);
    }

    // ==================================================================
    // ==================================================================
    // ==================================================================


    /**
     * This is the preferred way of modifying interest ops, giving a
     * Connection rather than a SelectionKey as input.  This way the
     * we can look it up and ensure the correct SelectionKey is always
     * used.
     *
     * @return Returns a <code>this</code> reference for chaining
     */
    public Listener modifyInterestOps(Connection connection,
            int op, boolean set) {
        return modifyInterestOps(connection.socketChannel().keyFor(selector), op,
                set);
    }

    /**
     * Batch version of modifyInterestOps().
     *
     * @return Returns a <code>this</code> reference for chaining
     */
    public Listener modifyInterestOpsBatch(Connection connection,
            int op, boolean set) {
        return modifyInterestOpsBatch(
                connection.socketChannel().keyFor(selector), op, set);
    }

    /**
     * Enqueue change to interest set of SelectionKey.  This is a workaround
     * for an NIO design error that makes it impossible to update interest
     * sets for a SelectionKey while a select is in progress -- and sometimes
     * you actually want to do this from other threads, which will then
     * block.  Hence, we make it possible to enqueue requests for
     * SelectionKey modification in the thread where select runs.
     *
     * @return Returns a <code>this</code> reference for chaining
     */
    public Listener modifyInterestOps(SelectionKey key, int op, boolean set) {
        synchronized (modifyInterestOpsQueue) {
            modifyInterestOpsQueue.addLast(new UpdateInterest(key, op, set));
        }
        selector.wakeup();
        return this;
    }

    /**
     * Does the same as modifyInterestOps(), but does not call
     * wakeup on the selector.  Allows adding more modifications
     * before we wake up the selector.
     *
     * @return Returns a <code>this</code> reference for chaining
     */
    public Listener modifyInterestOpsBatch(SelectionKey key,
            int op,
            boolean set) {
        synchronized (modifyInterestOpsQueue) {
            modifyInterestOpsQueue.addLast(new UpdateInterest(key, op, set));
        }
        return this;
    }

    /**
     * Signal that a batch update of SelectionKey is done and the
     * selector should be awoken.  Also see modifyInterestOps().
     *
     * @return Returns a <code>this</code> reference for chaining
     */
    public Listener modifyInterestOpsDone() {
        selector.wakeup();
        return this;
    }

    /**
     * Process enqueued changes to SelectionKeys. Also see
     * modifyInterestOps().
     */
    private void processModifyInterestOps() {
        synchronized (modifyInterestOpsQueue) {
            while (!modifyInterestOpsQueue.isEmpty()) {
                UpdateInterest u = modifyInterestOpsQueue.removeFirst();

                u.doUpdate();
            }
        }
    }

    // ==================================================================
    // ==================================================================
    // ==================================================================


    /**
     * Thread entry point
     */
    public void run() {
        log.fine("Started listener");
        try {
            selectLoop();
        } catch (Throwable t) {
            if (fatalErrorHandler != null) {
                fatalErrorHandler.handle(t, null);
            }
        }
    }

    /**
     * Check channels for readiness and deal with channels that have
     * pending operations.
     */
    private void selectLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            processNewConnections();
            processModifyInterestOps();

            try {
                int n = selector.select();

                if (0 == n) {
                    continue;
                }
            } catch (java.io.IOException e) {
                log.log(Level.WARNING, "error during select", e);
                return;
            }

            runSelectLoopPreHooks();

            Iterator<SelectionKey> i = selector.selectedKeys().iterator();

            while (i.hasNext()) {
                SelectionKey key = i.next();

                i.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isReadable()) {
                    performRead(key);
                    if (!key.isValid()) {
                        continue;
                    }
                }

                if (key.isWritable()) {
                    performWrite(key);
                    if (!key.isValid()) {
                        continue;
                    }
                }

                if (key.isConnectable()) {
                    performConnect(key);
                    if (!key.isValid()) {
                        continue;
                    }
                }

                if (key.isAcceptable()) {
                    performAccept(key);
                }
            }

            runSelectLoopPostHooks();
        }
    }

    /**
     * This method is used by the Acceptor to hand off newly accepted
     * connections to the Listener.  Note that this is run in the
     * context of the Acceptor thread, so doing things here versus
     * doing them in the acceptNewConnections(), which runs in the context
     * of the Listener thread, is a tradeoff that may need to be
     * re-evaluated
     *
     */
    public Connection addNewConnection(Connection newConn) {

        // ensure nonblocking and handle possible errors
        // if setting nonblocking fails.  this code is really redundant
        // but necessary because the older version of this method set
        // the connection nonblocking, and clients might still expect
        // this behavior.
        //
        SocketChannel channel = newConn.socketChannel();

        if (channel.isBlocking()) {
            try {
                channel.configureBlocking(false);
            } catch (java.nio.channels.IllegalBlockingModeException e) {
                log.log(Level.SEVERE, "Unable to set nonblocking", e);
                try {
                    channel.close();
                } catch (java.io.IOException ee) {
                    log.log(Level.WARNING, "channel close failed", ee);
                }
                return newConn;
            } catch (java.io.IOException e) {
                log.log(Level.SEVERE, "Unable to set nonblocking", e);
                return newConn;
            }
        }

        synchronized (newConnections) {
            newConnections.addLast(newConn);
        }
        selector.wakeup();
        return newConn;
    }

    /**
     * This method is called from the selectLoop() method in order to
     * process new incoming connections.
     */
    private synchronized void processNewConnections() {
        synchronized (newConnections) {
            while (!newConnections.isEmpty()) {
                Connection conn = newConnections.removeFirst();

                try {
                    conn.socketChannel().register(selector, conn.selectOps(),
                            conn);
                } catch (ClosedChannelException e) {
                    log.log(Level.WARNING, "register channel failed", e);
                    return;
                }
            }
        }
    }

    /**
     * Accept new connection.  This will loop over accept() until
     * there are no more new connections to accept.  If any error
     * occurs after a successful accept, the socket in question will
     * be discarded, but we will continue to try to accept new
     * connections if available.
     *
     */
    private void performAccept(SelectionKey key) {
        SocketChannel channel;
        ServerSocketChannel ssChannel;

        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        while (true) {
            try {
                ssChannel = (ServerSocketChannel) key.channel();
                channel = ssChannel.accept();

                // if for some reason there was no connection we just
                // ignore it.
                if (null == channel) {
                    return;
                }
            } catch (java.io.IOException e) {
                log.log(Level.WARNING, "accept failed", e);
                return;
            }

            // set nonblocking and handle possible errors
            try {
                channel.configureBlocking(false);
            } catch (java.nio.channels.IllegalBlockingModeException e) {
                log.log(Level.SEVERE, "Unable to set nonblocking", e);
                try {
                    channel.close();
                } catch (java.io.IOException ee) {
                    log.log(Level.WARNING, "channel close failed", ee);
                    continue;
                }
                continue;
            } catch (java.io.IOException e) {
                log.log(Level.WARNING, "IO error occurred", e);
                try {
                    channel.close();
                } catch (java.io.IOException ee) {
                    log.log(Level.WARNING, "channel close failed", ee);
                    continue;
                }
                continue;
            }

            ConnectionFactory factory = factories.get(ssChannel);
            Connection conn = factory.newConnection(channel, this);

            try {
                channel.register(selector, conn.selectOps(), conn);
            } catch (java.nio.channels.ClosedChannelException e) {
                log.log(Level.WARNING, "register channel failed", e);
            }
        }
    }

    /**
     * Complete asynchronous connect operation.  <em>Note that
     * asynchronous connect does not work properly in 1.4,
     * so you should not use this if you run anything older
     * than 1.5/5.0</em>.
     *
     */
    private void performConnect(SelectionKey key) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        Connection c = (Connection) key.attachment();

        try {
            c.connect();
        } catch (IOException e) {
            log.log(Level.FINE, "connect failed", e);
            try {
                c.close();
            } catch (IOException e2) {
                log.log(Level.FINE, "close failed", e);
            }
        }
    }

    /**
     * Perform read operation on channel which is now ready for reading
     */
    private void performRead(SelectionKey key) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        Connection c = (Connection) key.attachment();

        try {
            c.read();
        } catch (IOException e) {
            log.log(Level.FINE, "read failed", e);
            try {
                c.close();
            } catch (IOException e2) {
                log.log(Level.FINE, "close failed", e);
            }
        }
    }

    /**
     * Perform write operation(s) on channel which is now ready for
     * writing
     */
    private void performWrite(SelectionKey key) {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        Connection c = (Connection) key.attachment();

        try {
            c.write();
        } catch (IOException e) {
            log.log(Level.FINE, " write failed", e);
            try {
                c.close();
            } catch (IOException e2) {// ignore
            }
        }
    }

    // ============================================================
    // ==== connections made outside listener
    // ============================================================

    /**
     * Register a connection that was set up outside the listener.
     * Typically what we do when we actively reach out and connect
     * somewhere.
     */
    public void registerConnection(Connection connection) {
        synchronized (newConnections) {
            newConnections.addLast(connection);
        }
        selector.wakeup();
    }

    /**
     * Perform clean shutdown of Listener.
     *
     * TODO: implement
     */
    public void shutdown() {// make writing impossible
        // make listening on new ports impossible
        // close all listening connections (kill all listener threads)
        // flush outbound data if the connection wants it
        // close all connections
        // have some sort of grace-period before forcibly shutting down
    }
}
