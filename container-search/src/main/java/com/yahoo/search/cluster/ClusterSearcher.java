// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import com.yahoo.component.ComponentId;
import com.yahoo.container.protect.Error;
import com.yahoo.log.LogLevel;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.Hasher.NodeList;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implements clustering (failover and load balancing) over a set of client
 * connections to a homogenuos cluster of nodes. Searchers which wants to make
 * clustered connections to some service should use this.
 * <p>
 * This replaces the usual searcher methods by ones which have the same contract
 * and semantics but which takes an additional parameter which is the Connection
 * selected by the cluster searcher which the method should use. Overrides of
 * these connection methods <i>must not</i> call the super methods to pass on
 * but must use the methods on execution.
 * <p>
 * The type argument is the class (of any type) representing the connections.
 * The connection objects should implement a good toString to ease diagnostics.
 *
 * @author bratseth
 * @author Arne Bergene Fossaa
 */
public abstract class ClusterSearcher<T> extends PingableSearcher implements NodeManager<T> {

    private final Hasher<T> hasher;
    private final ClusterMonitor<T> monitor = new ClusterMonitor<>(this);

    /**
     * Creates a new cluster searcher
     *
     * @param id the id of this searcher
     * @param connections the connections of the cluster
     * @param internal whether or not this cluster is internal (part of the same installation)
     */
    public ClusterSearcher(ComponentId id, List<T> connections, boolean internal) {
        this(id, connections, new Hasher<T>(), internal);
    }

    public ClusterSearcher(ComponentId id, List<T> connections, Hasher<T> hasher, boolean internal) {
        super(id);
        this.hasher = hasher;
        for (T connection : connections) {
            monitor.add(connection, internal);
            hasher.add(connection);
        }
    }

    /**
     * Pinging a node, called from ClusterMonitor
     */
    @Override
    public final void ping(T p, Executor executor) {
        log(LogLevel.FINE, "Sending ping to: ", p);
        Pinger pinger = new Pinger(p);
        FutureTask<Pong> future = new FutureTask<>(pinger);

        executor.execute(future);
        Pong pong;
        Throwable logThrowable = null;

        try {
            pong = future.get(monitor.getConfiguration().getFailLimit(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            pong = new Pong(ErrorMessage.createUnspecifiedError("Ping was interrupted: " + p));
            logThrowable = e;
        } catch (ExecutionException e) {
            pong = new Pong(ErrorMessage.createUnspecifiedError("Execution was interrupted: " + p));
            logThrowable = e;
        } catch (LinkageError e) { // Typically Osgi woes
            pong = new Pong(ErrorMessage.createErrorInPluginSearcher("Class loading problem",e));
            logThrowable = e;
        } catch (TimeoutException e) {
            pong = new Pong(ErrorMessage.createNoAnswerWhenPingingNode("Ping thread timed out."));
        }
        future.cancel(true);

        if (pong.badResponse()) {
            monitor.failed(p, pong.getError(0));
            log(LogLevel.FINE, "Failed ping - ", pong);
        } else {
            monitor.responded(p);
            log(LogLevel.FINE, "Answered ping - ", p);
        }

        if (logThrowable != null) {
            StackTraceElement[] trace = logThrowable.getStackTrace();
            String traceAsString = null;
            if (trace != null) {
                StringBuilder b = new StringBuilder(": ");
                for (StackTraceElement k : trace) {
                    if (k == null) {
                        b.append("null\n");
                    } else {
                        b.append(k.toString()).append('\n');
                    }
                }
                traceAsString = b.toString();
            }
            getLogger().warning("Caught " + logThrowable.getClass().getName()
                                + " exception in " + getId().getName() + " ping"
                                + (trace == null ? ", no stack trace available." : traceAsString));
        }
    }

    /**
     * Pings this connection. Pings may be sent "out of band" at any time by the
     * monitoring subsystem to determine the status of this connection. If the
     * ping fails, it is ok both to set an error in the pong or to throw an
     * exception.
     */
    protected abstract Pong ping(Ping ping, T connection);

    protected T getFirstConnection(NodeList<T> nodes, int code, int trynum, Query query) {
        return nodes.select(code, trynum);
    }

    @Override
    public final Result search(Query query, Execution execution) {
        int tries = 0;

        Hasher.NodeList<T> nodes = getHasher().getNodes();

        if (nodes.getNodeCount() == 0)
            return search(query, execution, ErrorMessage
                    .createNoBackendsInService("No nodes in service in " + this + " (" + monitor.nodeMonitors().size()
                                               + " was configured, none is responding)"));

        int code = query.hashCode();
        Result result;
        T connection = getFirstConnection(nodes, code, tries, query);
        do {
            // The loop is in case there are other searchers available able to produce results
            if (connection == null)
                return search(query, execution, ErrorMessage
                        .createNoBackendsInService("No in node could handle " + query + " according to " +
                                                   hasher + " in " + this));
            if (timedOut(query))
                return new Result(query, ErrorMessage.createTimeout("No time left for searching"));

            if (query.getTraceLevel() >= 8)
                query.trace("Trying " + connection, false, 8);

            result = robustSearch(query, execution, connection);

            if (!shouldRetry(query, result))
                return result;

            if (query.getTraceLevel() >= 6)
                query.trace("Error from connection " + connection + " : " + result.hits().getError(), false, 6);

            if (result.hits().getError().getCode() == Error.TIMEOUT.code)
                return result; // Retry is unlikely to help

            log(LogLevel.FINER, "No result, checking for timeout.");
            tries++;
            connection = nodes.select(code, tries);
        } while (tries < nodes.getNodeCount());

        // only error result gets returned here.
        return result;

    }

    /**
     * Returns whether this query and result should be retried against another
     * connection if possible. This default implementation returns true if the
     * result contains some error.
     */
    protected boolean shouldRetry(Query query, Result result) {
        return result.hits().getError() != null;
    }

    /**
     * This is called (instead of search(quer,execution,connextion) to handle
     * searches where no (suitable) backend was available. The default
     * implementation returns an error result.
     */
    protected Result search(Query query, Execution execution, ErrorMessage message) {
        return new Result(query, message);
    }

    /**
     * Call search(Query,Execution,T) and handle any exceptions returned which
     * we do not want to propagate upwards By default this catches all runtime
     * exceptions and puts them into the result
     */
    protected Result robustSearch(Query query, Execution execution, T connection) {
        Result result;
        try {
            result = search(query, execution, connection);
        } catch (RuntimeException e) { //TODO: Exceptions should not be used to signal backend communication errors
            log(LogLevel.WARNING, "An exception occurred while invoking backend searcher.", e);
            result = new Result(query, ErrorMessage
                    .createBackendCommunicationError("Failed calling "
                            + connection + " in " + this + " for " + query
                            + ": " + Exceptions.toMessageString(e)));
        }

        if (result == null)
            result = new Result(query, ErrorMessage
                    .createBackendCommunicationError("No result returned in "
                            + this + " from " + connection + " for " + query));

        if (result.hits().getError() != null) {
            log(LogLevel.FINE, "FAILED: ", query);
        } else if (!result.isCached()) {
            log(LogLevel.FINE, "WORKING: ", query);
        } else {
            log(LogLevel.FINE, "CACHE HIT: ", query);
        }
        return result;
    }

    /**
     * Perform the search against the given connection. Return a result
     * containing an error or throw an exception on failures.
     */
    protected abstract Result search(Query query, Execution execution, T connection);

    public @Override
    final void fill(Result result, String summaryClass, Execution execution) {
        Query query = result.getQuery();
        Hasher.NodeList<T> nodes = getHasher().getNodes();
        int code = query.hashCode();

        T connection = nodes.select(code, 0);
        if (connection != null) {
            if (timedOut(query)) {
                result.hits().addError(
                        ErrorMessage.createTimeout(
                                "No time left to get summaries for "
                                + result));
            } else {
                // query.setTimeout(getNodeTimeout(query));
                doFill(connection, result, summaryClass, execution);
            }
        } else {
            result.hits().addError(
                    ErrorMessage.createNoBackendsInService("Could not fill '"
                            + result + "' in '" + this + "'"));
        }
    }

    private void doFill(T connection, Result result, String summaryClass, Execution execution) {
        try {
            fill(result, summaryClass, execution, connection);
        } catch (RuntimeException e) {
            result.hits().addError(
                    ErrorMessage
                            .createBackendCommunicationError("Error filling "
                                    + result + " from " + connection + ": "
                                    + Exceptions.toMessageString(e)));
        }
        if (result.hits().getError() != null) {
            log(LogLevel.FINE, "FAILED: ", result.getQuery());
        } else if (!result.isCached()) {
            log(LogLevel.FINE, "WORKING: ", result.getQuery());
        } else {
            log(LogLevel.FINE, "CACHE HIT: " + result.getQuery());
        }
    }

    /**
     * Perform the fill against the given connection. Add an error to the result
     * or throw an exception on failures.
     */
    protected abstract void fill(Result result, String summaryClass, Execution execution, T connection);

    /** NodeManager method, called from ClusterMonitor */
    @Override
    public void working(T node) {
        getHasher().add(node);
    }

    /** NodeManager method, called from ClusterMonitor */
    @Override
    public void failed(T node) {
        getHasher().remove(node);
    }

    /** Returns the hasher used internally in this. Do not mutate this hasher while in use. */
    public Hasher<T> getHasher() { return hasher; }

    /** Returns the monitor of these nodes */
    public ClusterMonitor<T> getMonitor() { return monitor; }

    /** Returns true if this query has timed out now */
    protected boolean timedOut(Query query) {
        return query.getDurationTime() >= query.getTimeout();
    }

    protected void log(java.util.logging.Level level, Object... objects) {
        if ( ! getLogger().isLoggable(level)) return;

        StringBuilder sb = new StringBuilder();
        for (Object object : objects)
            sb.append(object);
        getLogger().log(level, sb.toString());
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        monitor.shutdown();
    }

    private class Pinger implements Callable<Pong> {

        private T connection;

        public Pinger(T connection) {
            this.connection = connection;
        }

        public Pong call() {
            try {
                return ping(new Ping(monitor.getConfiguration().getRequestTimeout()), connection);
            } catch (RuntimeException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Exception when pinging "
                                                                             + connection + ": "
                                                                             + Exceptions.toMessageString(e)));
            }
        }

    }

}
