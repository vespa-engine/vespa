// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.net.InetSocketAddress;


/**
 * Class for accepting new connections in separate thread.
 *
 * @author <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 *
 */
public class Acceptor extends Thread {
    private static Logger log = Logger.getLogger(Acceptor.class.getName());

    private int port;
    ServerSocketChannel socket;
    private Listener listener;
    private boolean initialized = false;
    private ConnectionFactory factory;
    private FatalErrorHandler fatalErrorHandler;

    public Acceptor(Listener listener, ConnectionFactory factory, int port) {
        super("Acceptor-" + listener.getName() + "-" + port);
        this.listener = listener;
        this.factory = factory;
        this.port = port;
    }

    public Acceptor listen() throws IOException {
        socket = ServerSocketChannel.open();
        socket.configureBlocking(true);
        socket.socket().setReuseAddress(true);
        socket.socket().bind(new InetSocketAddress(port));
        initialized = true;
        return this;
    }

    /**
     * Register a handler for fatal errors.
     *
     * @param f The FatalErrorHandler instance to be registered
     */
    public synchronized void setFatalErrorHandler(FatalErrorHandler f) {
        fatalErrorHandler = f;
    }

    public void run() {
        try {
            log.fine("Acceptor thread started");
            if (!initialized) {
                log.severe("Acceptor was not initialized.  aborting");
                return;
            }

            while (!isInterrupted()) {
                SocketChannel c = null; // hush jikes

                try {
                    c = socket.accept();
                    c.configureBlocking(false);
                    listener.addNewConnection(factory.newConnection(c, listener));
                } catch (java.nio.channels.IllegalBlockingModeException e) {
                    log.log(Level.SEVERE, "Unable to set nonblocking", e);
                    try {
                        if (c != null) {
                            c.close();
                        }
                    } catch (IOException ee) {}
                } catch (IOException e) {
                    log.log(Level.WARNING,
                            "Error accepting connection on port=" + port, e);
                    try {
                        if (c != null) {
                            c.close();
                        }
                    } catch (IOException ee) {}
                }
            }
        } catch (Throwable t) {
            if (fatalErrorHandler != null) {
                fatalErrorHandler.handle(t, null);
            }
        }
    }
}
