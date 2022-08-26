// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class used to listen on a network socket. A separate thread is
 * used to accept connections and register them with the underlying
 * transport thread. To create an acceptor you need to invoke the
 * {@link Supervisor#listen listen} method in the {@link Supervisor}
 * class.
 */
public class Acceptor {

    private class Run implements Runnable {
        public void run() {
            try {
                Acceptor.this.run();
            } catch (Throwable problem) {
                parent.handleFailure(problem, Acceptor.this);
            }
        }
    }

    private final static Logger log = Logger.getLogger(Acceptor.class.getName());

    private final Thread         thread = new Thread(new Run(), "<jrt-acceptor>");
    private final CountDownLatch shutdownGate = new CountDownLatch(1);
    private final Transport      parent;
    private final Supervisor     owner;

    private final ServerSocketChannel serverChannel;

    Acceptor(Transport parent, Supervisor owner, Spec spec) throws ListenFailedException {
        this.parent = parent;
        this.owner  = owner;

        if (spec.malformed())
            throw new ListenFailedException("Malformed spec '" + spec + "'");

        serverChannel = createServerSocketChannel(spec);

        thread.setDaemon(true);
        thread.start();
    }

    private static ServerSocketChannel createServerSocketChannel(Spec spec) throws ListenFailedException {
        ServerSocketChannel serverChannel = null;
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(true);
            if (spec.port() != 0) {
                serverChannel.socket().setReuseAddress(true);
            }
            serverChannel.socket().bind(spec.resolveAddress(), 500);
        } catch (Exception e) {
            if (serverChannel != null) {
                try { serverChannel.socket().close(); } catch (Exception ignore) {}
            }
            throw new ListenFailedException("Failed to listen to " + spec, e);
        }
        return serverChannel;
    }

    /**
     * Obtain the local port number this Acceptor is listening to. If
     * this Acceptor is no longer listening (it has been shut down),
     * -1 will be returned.
     *
     * @return listening port, or -1 if not listening
     **/
    public int port() {
        if (!serverChannel.isOpen()) {
            return -1;
        }
        return serverChannel.socket().getLocalPort();
    }

    /**
     * Obtain the Spec for the local port and host interface this Acceptor
     * is listening to.  If this Acceptor is no longer listening (it has
     * been shut down), null will be returned.
     *
     * @return listening spec, or null if not listening
     */
    public Spec spec() {
        if ( ! serverChannel.isOpen()) {
            return null;
        }
        return new Spec(serverChannel.socket().getInetAddress().getHostName(),
                        serverChannel.socket().getLocalPort());
    }

    private void run() {
        while (serverChannel.isOpen()) {
            try {
                TransportThread tt = parent.selectThread();
                tt.addConnection(new Connection(tt, owner, serverChannel.accept(), parent.getTcpNoDelay()));
                tt.sync();
            } catch (ClosedChannelException ignore) {
            } catch (Exception e) {
                log.log(Level.WARNING, "Error accepting connection", e);
            }
        }
        while (true) {
            try {
                shutdownGate.await();
                return;
            } catch (InterruptedException ignore) {}
        }
    }

    /**
     * Initiate controlled shutdown of the acceptor thread
     *
     * @return this object, to enable chaining with {@link #join join}
     **/
    public Acceptor shutdown() {
        try {
            serverChannel.socket().close();
        } catch (Exception e1) {
            log.log(Level.WARNING, "Error closing server socket", e1);
            Thread.yield(); // throw some salt over the shoulder
            try {
                serverChannel.socket().close();
            } catch (Exception e2) {
                log.log(Level.WARNING, "Error closing server socket", e2);
                Thread.yield(); // throw some salt over the shoulder
                try {
                    serverChannel.socket().close();
                } catch (Exception e3) {
                    log.log(Level.WARNING, "Error closing server socket", e3);
                    throw new Error("Error closing server socket 3 times", e3);
                }
            }
        } finally {
            shutdownGate.countDown();
        }
        return this;
    }

    /**
     * Wait for the acceptor thread to finish
     **/
    public void join() {
        while (true) {
            try {
                thread.join();
                return;
            } catch (InterruptedException ignore) {}
        }
    }
}
