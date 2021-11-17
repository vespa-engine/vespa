// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pool of JRT connections to a config source (either a config server or a config proxy).
 * The current connection is chosen randomly when calling {@link #switchConnection(Connection)}
 * (it will continue to use the same connection if there is only one source).
 * The current connection is available with {@link #getCurrent()}.
 *
 * @author Gunnar Gauslaa Bergem
 * @author hmusum
 */
public class JRTConnectionPool implements ConnectionPool {

    private static final Logger log = Logger.getLogger(JRTConnectionPool.class.getName());

    private final Supervisor supervisor;
    private final Map<String, JRTConnection> connections = new LinkedHashMap<>();
    private final String poolName;

    // The config sources used by this connection pool.
    private ConfigSourceSet sourceSet = null;

    // The current connection used by this connection pool.
    private volatile JRTConnection currentConnection;

    public JRTConnectionPool(ConfigSourceSet sourceSet) {
        this(sourceSet, new Supervisor(new Transport("config-pool-" + sourceSet.hashCode())).setDropEmptyBuffers(true));
    }

    public JRTConnectionPool(ConfigSourceSet sourceSet, Supervisor supervisor) {
        if (sourceSet.getSources().isEmpty()) throw new IllegalArgumentException("sourceSet cannot be empty");
        this.supervisor = supervisor;
        this.poolName = supervisor.transport().getName();
        addSources(sourceSet);
    }

    public void addSources(ConfigSourceSet sourceSet) {
        this.sourceSet = sourceSet;
        synchronized (connections) {
            for (String address : sourceSet.getSources()) {
                connections.put(address, new JRTConnection(address, supervisor));
            }
        }
        currentConnection = initialize();
    }

    /**
     * Returns the current JRTConnection instance
     *
     * @return a JRTConnection
     */
    public synchronized JRTConnection getCurrent() {
        return currentConnection;
    }

    @Override
    public synchronized JRTConnection switchConnection(Connection failingConnection) {
        List<JRTConnection> sources = getSources();
        if (sources.size() <= 1) return currentConnection;

        if ( ! currentConnection.equals(failingConnection)) return currentConnection;

        return switchConnection();
    }

    /**
     * Preconditions:
     * 1. the current connection is unhealthy and should not be selected when switching
     * 2. There is more than 1 source.
     */
    synchronized JRTConnection switchConnection() {
        if (getSources().size() <= 1) throw new IllegalStateException("Cannot switch connection, not enough sources");

        List<JRTConnection> sourceCandidates = getSources();
        sourceCandidates.remove(currentConnection);
        JRTConnection newConnection = pickNewConnectionRandomly(sourceCandidates);
        log.log(Level.INFO, () -> poolName + ": Switching from " + currentConnection + " to " + newConnection);
        return currentConnection = newConnection;
    }

    public synchronized JRTConnection initialize() {
        return pickNewConnectionRandomly(getSources());
    }

    protected JRTConnection pickNewConnectionRandomly(List<JRTConnection> sources) {
        return sources.get(ThreadLocalRandom.current().nextInt(0, sources.size()));
    }

    protected List<JRTConnection> getSources() {
        List<JRTConnection> ret;
        synchronized (connections) {
            ret = new ArrayList<>(connections.values());
        }
        return ret;
    }

    ConfigSourceSet getSourceSet() {
        return sourceSet;
    }

    public JRTConnectionPool updateSources(List<String> addresses) {
        ConfigSourceSet newSources = new ConfigSourceSet(addresses);
        return updateSources(newSources);
    }

    public JRTConnectionPool updateSources(ConfigSourceSet sourceSet) {
        synchronized (connections) {
            for (JRTConnection conn : connections.values()) {
                conn.getTarget().close();
            }
            connections.clear();
            addSources(sourceSet);
        }
        return this;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(poolName + ": ");
        synchronized (connections) {
            for (JRTConnection conn : connections.values()) {
                sb.append(conn.toString());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public void close() {
        supervisor.transport().shutdown().join();
    }

    @Override
    public int getSize() {
        synchronized (connections) {
            return connections.size();
        }
    }

}
