// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.mplex;


import com.yahoo.fs4.*;
import com.yahoo.io.Connection;
import com.yahoo.io.ConnectionFactory;
import com.yahoo.io.Listener;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Bjorn Borud
 */
public class Backend implements ConnectionFactory {

    public static final class BackendStatistics {

        public final int activeConnections;
        public final int passiveConnections;

        public BackendStatistics(int activeConnections, int passiveConnections) {
            this.activeConnections = activeConnections;
            this.passiveConnections = passiveConnections;
        }

        @Override
        public String toString() {
            return activeConnections + "/" + totalConnections();
        }

        public int totalConnections() {
            return activeConnections + passiveConnections;
        }
    }

    private static final Logger log = Logger.getLogger(Backend.class.getName());

    private final ListenerPool listeners;
    private final InetSocketAddress address;
    private final String host;
    private final int port;
    private final Map<Integer, FS4Channel> activeChannels = new HashMap<>();
    private int channelId = 0;
    private boolean shutdownInitiated = false;

    /** Whether we are currently in the state of not being able to connect, to avoid repeated logging */
    private boolean areInSocketNotConnectableState = false;

    private final LinkedList<FS4Channel> pingChannels = new LinkedList<>();
    private final PacketListener packetListener;
    private final ConnectionPool connectionPool;
    private final PacketDumper packetDumper;
    private final AtomicInteger connectionCount = new AtomicInteger(0);


    /**
     * For unit testing.  do not use
     */
    protected Backend() {
        listeners = null;
        host = null;
        port = 0;
        packetListener = null;
        packetDumper = null;
        address = null;
        connectionPool = new ConnectionPool();
    }

    public Backend(String host, int port, String serverDiscriminator, ListenerPool listenerPool, ConnectionPool connectionPool) {
        String fileNamePattern = "qrs." + serverDiscriminator + '.' + host + ":" + port + ".%s" + ".dump";
        packetDumper = new PacketDumper(new File(Defaults.getDefaults().underVespaHome("logs/vespa/qrs/")),
                                        fileNamePattern);
        packetListener = new PacketNotificationsBroadcaster(packetDumper, new PacketQueryTracer());
        this.listeners = listenerPool;
        this.host = host;
        this.port = port;
        address = new InetSocketAddress(host, port);
        this.connectionPool = connectionPool;
    }

    private void logWarning(String attemptDescription, Exception e) {
        log.log(Level.WARNING, "Exception on " + attemptDescription + " '" + host + ":" + port + "': " + Exceptions.toMessageString(e));
    }

    private void logInfo(String attemptDescription, Exception e) {
        log.log(Level.INFO, "Exception on " + attemptDescription + " '" + host + ":" + port + "': " + Exceptions.toMessageString(e));
    }

    // ============================================================
    // ==== connection pool stuff
    // ============================================================


    /**
     * Fetch a connection from the connection pool.  If the pool
     * is empty we create a connection.
     */
    private FS4Connection getConnection() throws IOException {
        FS4Connection connection = connectionPool.getConnection();
        if (connection == null) {
            // if pool was empty create one:
            connection = createConnection();
        }
        return connection;
    }

    /**
     * Return a connection to the connection pool.  If the
     * connection is not valid anymore we drop it, ie. do not
     * put it into the pool.
     */
    public void returnConnection(FS4Connection connection) {
        connectionPool.releaseConnection(connection);
    }

    /**
     * Create a new connection to the target for this backend.
     */
    private FS4Connection createConnection() throws IOException {
        SocketChannel socket = SocketChannel.open();
        try {
            connectSocket(socket);
        } catch (Exception e) {
            // was warning, see VESPA-1922
            if ( ! areInSocketNotConnectableState) {
                logInfo("connecting to", e);
            }
            areInSocketNotConnectableState = true;
            socket.close();
            return null;
        }
        areInSocketNotConnectableState = false;
        int listenerId = connectionCount.getAndIncrement()%listeners.size();
        Listener listener = listeners.get(listenerId);
        FS4Connection connection = new FS4Connection(socket, listener, this, packetListener);
        listener.registerConnection(connection);

        log.fine("Created new connection to " + host + ":" + port);
        connectionPool.createdConnection();
        return connection;
    }

    private void connectSocket(SocketChannel socket) throws IOException {
        socket.configureBlocking(false);

        boolean connected = socket.connect(address);

        // wait for connection
        if (!connected) {
            long timeBarrier = System.currentTimeMillis() + 20L;
            while (true) {
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Received InterruptedException while waiting for socket to connect.", e);
                }
                // don't care whether it's spurious wakeup
                connected = socket.finishConnect();
                if (connected || System.currentTimeMillis() > timeBarrier) {
                    break;
                }
            }
        }

        // did we get a connection?
        if ( !connected) {
            throw new IllegalArgumentException("Could not create connection to dispatcher on "
                    + address.getHostName() + ":" + address.getPort());
        }
        socket.socket().
        setTcpNoDelay(true);
    }


    //============================================================
    //==== channel management
    //============================================================

    /**
     * Open a new channel to fdispatch.  Analogous to the "Channel"
     * concept as used in FS4.
     */
    public FS4Channel openChannel () {
        int cachedChannelId;
        synchronized (this) {
            if (channelId >= ((1 << 31) - 2)) {
                channelId = 0;
            }
            cachedChannelId = channelId;
            channelId += 2;
        }
        Integer id = cachedChannelId;
        FS4Channel chan = new FS4Channel(this, id);
        synchronized (activeChannels) {
            activeChannels.put(id, chan);
        }
        return chan;
    }

    public FS4Channel openPingChannel () {
        FS4Channel chan = FS4Channel.createPingChannel(this);
        synchronized (pingChannels) {
            pingChannels.add(chan);
        }
        return chan;
    }

    /**
     * Get the remote address for this Backend. This method
     * has package access only, because it is really only of
     * importance to FS4Channel for writing slightly more sensible
     * log messages.
     * @return Returns the address (host, port) for this Backend.
     */
    InetSocketAddress getAddress() {
        return address;
    }

    /**
     * Get an active channel by id.
     *
     * @param id the (fs4) channel id
     * @return returns the (fs4) channel associated with this id
     *         or <code>null</code> if the channel is not in the
     *         set of active channels.
     */
    public FS4Channel getChannel(Integer id) {
        synchronized (activeChannels) {
            return activeChannels.get(id);
        }
    }

    /**
     * Return the first channel in the queue waiting for pings or
     * <code>null</code> if none.
     */
    public FS4Channel getPingChannel () {
        synchronized (pingChannels) {
            return (pingChannels.isEmpty()) ? null : pingChannels.getFirst();
        }
    }

    /**
     * Get an active channel by id.  This is a wrapper for the method
     * that takes the id as an Integer.
     *
     * @param id The (fs4) channel id
     * @return Returns the (fs4) channel associated with this id
     *         or <code>null</code> if the channel is not in the
     *         set of active channels.
     */
    public FS4Channel getChannel (int id) {
        return getChannel(new Integer(id));
    }

    /**
     * Remove a channel.  We do not want this method to be called
     * directly by the client -- removal of channels should be done
     * by calling the close() method of the channel.
     *
     * @param id The (fs4) channel id
     * @return Removes and returns the (fs4) channel associated
     *         with this id or <code>null</code> if the channel is
     *         not in the set of active channels.
     */
    protected FS4Channel removeChannel (Integer id) {
        synchronized (activeChannels) {
            return activeChannels.remove(id);
        }
    }

    /**
     * Remove a ping channel.  We do not want this method to be called
     * directly by the client -- removal of channels should be done
     * by calling the close() method of the channel.
     *
     * @return Removes and returns the (fs4) channel first in
     *         the queue of ping channels or <code>null</code>
     *         if there are no active ping channels.
     */
    protected FS4Channel removePingChannel () {
        synchronized (pingChannels) {
            if (pingChannels.isEmpty())
                return null;
            return pingChannels.removeFirst();
        }
    }
    //============================================================
    //==== packet sending and reception
    //============================================================

    protected boolean sendPacket(BasicPacket packet, Integer channelId) throws IOException {
        if (shutdownInitiated) {
            log.fine("Tried to send packet after shutdown initiated.  Ignored.");
            return false;
        }

        FS4Connection connection = null;
        try {
            connection = getConnection();
            if (connection == null) {
                return false;
            }
            connection.sendPacket(packet, channelId);
        }
        finally {
            if (connection != null) {
                returnConnection(connection);
            }
        }

        return true;
    }

    /**
     * When a connection receives a packet, it uses this method to
     * dispatch the packet to the right FS4Channel.  If the corresponding
     * FS4Channel does not exist the packet is dropped and a message is
     * logged saying so.
     */
    protected void receivePacket(BasicPacket packet) {
        FS4Channel fs4;
        if (packet.hasChannelId())
            fs4 = getChannel(((Packet)packet).getChannel());
        else
            fs4 = getPingChannel();

        // channel does not exist
        if (fs4 == null) {
            return;
        }
        try {
            fs4.addPacket(packet);
        }
        catch (InterruptedException e) {
            log.info("Interrupted during packet adding. Packet = " + packet.toString());
            Thread.currentThread().interrupt();
        }
        catch (InvalidChannelException e) {
            log.log(Level.WARNING, "Channel was invalid. Packet = " + packet.toString()
                    + " Backend probably sent data pertaining an old request,"
                    + " system may be overloaded.");
        }
    }

    /**
     * This method should be used to ensure graceful shutdown of the backend.
     */
    public void shutdown() {
        log.fine("shutting down");
        if (shutdownInitiated) {
            throw new IllegalStateException("Shutdown already in progress");
        }
        shutdownInitiated = true;
    }

    public void close() {
        for (Connection c = connectionPool.getConnection(); c != null; c = connectionPool.getConnection()) {
            try {
                c.close();
            } catch (IOException e) {
                logWarning("closing", e);
            }
        }
    }

    /**
     * Connection factory used by the Listener class.
     */
    public Connection newConnection(SocketChannel channel, Listener listener) {
        return new FS4Connection(channel, listener, this, packetListener);
    }

    public String toString () {
        return("Backend/" + host + ":" + port);
    }

    public BackendStatistics getStatistics() {
        synchronized (connectionPool) { //ensure consistent values
            return new BackendStatistics(connectionPool.activeConnections(), connectionPool.passiveConnections());
        }
    }

    public void dumpPackets(final PacketDumper.PacketType packetType, final boolean on) throws IOException {
        packetDumper.dumpPackets(packetType, on);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

}
