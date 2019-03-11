// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.ChannelTimeoutException;
import com.yahoo.fs4.PingPacket;
import com.yahoo.fs4.PongPacket;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.fs4.mplex.InvalidChannelException;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.querytransform.QueryRewrite;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;

import static com.yahoo.container.util.Util.quote;

/**
 * The searcher which forwards queries to fdispatch nodes, using the fnet/fs4
 * network layer.
 *
 * @author  bratseth
 */
// TODO: Clean up all the duplication in the various search methods by
// switching to doing all the error handling using exceptions below doSearch2.
// Right now half is done by exceptions handled in doSearch2 and half by setting
// errors on results and returning them. It could be handy to create a QueryHandlingErrorException
// or similar which could wrap an error message, and then just always throw that and
// catch and unwrap into a results with an error in high level methods.  -Jon
public class FastSearcher extends VespaBackEndSearcher {

    /** If this is turned on this will make search queries directly to the local search node when possible */
    private final static CompoundName dispatchDirect = new CompoundName("dispatch.direct");

    /** Used to dispatch directly to search nodes over RPC, replacing the old fnet communication path */
    private final Dispatcher dispatcher;

    private final Backend dispatchBackend;

    /**
     * Creates a Fastsearcher.
     *
     * @param dispatchBackend       The backend object containing the connection to the dispatch node this should talk to
     *                      over the fs4 protocol
     * @param fs4ResourcePool the resource pool used to create direct connections to the local search nodes when
     *                        bypassing the dispatch node
     * @param dispatcher the dispatcher used (when enabled) to send summary requests over the rpc protocol.
     *                   Eventually we will move everything to this protocol and never use dispatch nodes.
     *                   At that point we won't need a cluster searcher above this to select and pass the right
     *                   backend.
     * @param docSumParams  document summary parameters
     * @param clusterParams the cluster number, and other cluster backend parameters
     * @param documentdbInfoConfig document database parameters
     */
    public FastSearcher(Backend dispatchBackend, FS4ResourcePool fs4ResourcePool, Dispatcher dispatcher,
                        SummaryParameters docSumParams, ClusterParams clusterParams,
                        DocumentdbInfoConfig documentdbInfoConfig) {
        init(fs4ResourcePool.getServerId(), docSumParams, clusterParams, documentdbInfoConfig);
        this.dispatchBackend = dispatchBackend;
        this.dispatcher = dispatcher;
    }

    /**
     * Pings the backend. Does not propagate to other searchers.
     */
    @Override
    public Pong ping(Ping ping, Execution execution) {
        return ping(ping, dispatchBackend, getName());
    }

    public static Pong ping(Ping ping, Backend backend, String name) {
        FS4Channel channel = backend.openPingChannel();

        // If you want to change this code, you need to understand
        // com.yahoo.prelude.cluster.ClusterSearcher.ping(Searcher) and
        // com.yahoo.prelude.cluster.TrafficNodeMonitor.failed(ErrorMessage)
        try {
            PingPacket pingPacket = new PingPacket();
            try {
                boolean couldSend = channel.sendPacket(pingPacket);
                if ( ! couldSend) {
                    return new Pong(ErrorMessage.createBackendCommunicationError("Could not ping " + name));
                }
            } catch (InvalidChannelException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Invalid channel " + name));
            } catch (IllegalStateException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Illegal state in FS4: " + e.getMessage()));
            } catch (IOException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("IO error while sending ping: " + e.getMessage()));
            }

            // We should only get a single packet
            BasicPacket[] packets;

            try {
                packets = channel.receivePackets(ping.getTimeout(), 1);
            } catch (ChannelTimeoutException e) {
                return new Pong(ErrorMessage.createNoAnswerWhenPingingNode("timeout while waiting for fdispatch for " + name));
            } catch (InvalidChannelException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Invalid channel for " + name));
            }

            if (packets.length == 0) {
                return new Pong(ErrorMessage.createBackendCommunicationError(name + " got no packets back"));
            }

            try {
                packets[0].ensureInstanceOf(PongPacket.class, name);
            } catch (TimeoutException e) {
                return new Pong(ErrorMessage.createTimeout(e.getMessage()));
            } catch (IOException e) {
                return new Pong(ErrorMessage.createBackendCommunicationError("Unexpected packet class returned after ping: " + e.getMessage()));
            }
            return new Pong((PongPacket)packets[0]);
        } finally {
            if (channel != null) {
                channel.close();
            }
        }
    }

    @Override
    protected void transformQuery(Query query) {
        QueryRewrite.rewriteSddocname(query);
    }

    @Override
    public Result doSearch2(Query query, QueryPacket queryPacket, Execution execution) {
        if (dispatcher.searchCluster().groupSize() == 1)
            forceSinglePassGrouping(query);
        try(SearchInvoker invoker = getSearchInvoker(query)) {
            Result result = invoker.search(query, queryPacket, execution);

            if (query.properties().getBoolean(Ranking.RANKFEATURES, false)) {
                // There is currently no correct choice for which
                // summary class we want to fetch at this point. If we
                // fetch the one selected by the user it may not
                // contain the data we need. If we fetch the default
                // one we end up fetching docsums twice unless the
                // user also requested the default one.
                fill(result, query.getPresentation().getSummary(), execution); // ARGH
            }
            return result;
        } catch (TimeoutException e) {
            return new Result(query,ErrorMessage.createTimeout(e.getMessage()));
        } catch (IOException e) {
            Result result = new Result(query);
            if (query.getTraceLevel() >= 1)
                query.trace(getName() + " error response: " + result, false, 1);
            result.hits().addError(ErrorMessage.createBackendCommunicationError(getName() + " failed: "+ e.getMessage()));
            return result;
        }
    }

    /**
     * Perform a partial docsum fill for a temporary result
     * representing a partition of the complete fill request.
     *
     * @param result result containing a partition of the unfilled hits
     * @param summaryClass the summary class we want to fill with
     **/
    @Override
    protected void doPartialFill(Result result, String summaryClass) {
        if (result.isFilled(summaryClass)) return;

        Query query = result.getQuery();
        traceQuery(getName(), "fill", query, query.getOffset(), query.getHits(), 1, quotedSummaryClass(summaryClass));

        try (FillInvoker invoker = getFillInvoker(result)) {
            invoker.fill(result, summaryClass);
        }
    }

    /** When we only search a single node, doing all grouping in one pass is more efficient */
    private void forceSinglePassGrouping(Query query) {
        for (GroupingRequest groupingRequest : query.getSelect().getGrouping())
            forceSinglePassGrouping(groupingRequest.getRootOperation());
    }

    private void forceSinglePassGrouping(GroupingOperation operation) {
        operation.setForceSinglePass(true);
        for (GroupingOperation childOperation : operation.getChildren())
            forceSinglePassGrouping(childOperation);
    }

    /**
     * Returns an invocation object for use in a single search request. The specific implementation returned
     * depends on query properties with the default being an invoker that interfaces with a dispatcher
     * on the same host.
     */
    private SearchInvoker getSearchInvoker(Query query) {
        Optional<SearchInvoker> invoker = dispatcher.getSearchInvoker(query, this);
        if (invoker.isPresent()) {
            return invoker.get();
        }

        Optional<Node> direct = getDirectNode(query);
        if(direct.isPresent()) {
            return dispatcher.getFS4InvokerFactory().createSearchInvoker(this, query, direct.get());
        }
        return new FS4SearchInvoker(this, query, dispatchBackend.openChannel(), Optional.empty());
    }

    /**
     * Returns an invocation object for use in a single fill request. The specific implementation returned
     * depends on query properties with the default being an invoker that uses RPC to interface with
     * content nodes.
     */
    private FillInvoker getFillInvoker(Result result) {
        Query query = result.getQuery();
        Optional<FillInvoker> invoker = dispatcher.getFillInvoker(result, this);
        if (invoker.isPresent()) {
            return invoker.get();
        }

        Optional<Node> direct = getDirectNode(query);
        if (direct.isPresent()) {
            return dispatcher.getFS4InvokerFactory().createFillInvoker(this, result, direct.get());
        }
        return new FS4FillInvoker(this, query, dispatchBackend);
    }

    /**
     * If the query can be directed to a single local content node, returns that node. Otherwise,
     * returns an empty value.
     */
    private Optional<Node> getDirectNode(Query query) {
        if (!query.properties().getBoolean(dispatchDirect, true))
            return Optional.empty();
        if (query.properties().getBoolean(com.yahoo.search.query.Model.ESTIMATE))
            return Optional.empty();

        Optional<Node> directDispatchRecipient = dispatcher.searchCluster().directDispatchTarget();
        if (!directDispatchRecipient.isPresent())
            return Optional.empty();

        // Dispatch directly to the single, local search node
        Node local = directDispatchRecipient.get();
        query.trace(false, 2, "Dispatching directly to ", local);
        return Optional.of(local);
    }

    private static @NonNull Optional<String> quotedSummaryClass(String summaryClass) {
        return Optional.of(summaryClass == null ? "[null]" : quote(summaryClass));
    }

    public String toString() {
        return "fast searcher (" + getName() + ") " + dispatchBackend;
    }

    protected boolean isLoggingFine() {
        return getLogger().isLoggable(Level.FINE);
    }

}
