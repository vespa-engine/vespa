// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.compress.CompressionType;
import com.yahoo.container.search.LegacyEmulationConfig;
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
import com.yahoo.search.dispatch.CloseableChannel;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.SearchCluster;
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

    /** Unless turned off this will fill summaries by dispatching directly to search nodes over RPC when possible */
    private final static CompoundName dispatchSummaries = new CompoundName("dispatch.summaries");

    /** The compression method which will be used with rpc dispatch. "lz4" (default) and "none" is supported. */
    private final static CompoundName dispatchCompression = new CompoundName("dispatch.compression");

    /** If enabled, the dispatcher internal to the search container will be preferred over fdispatch
     * whenever possible */
    private static final CompoundName dispatchInternal = new CompoundName("dispatch.internal");

    /** Used to dispatch directly to search nodes over RPC, replacing the old fnet communication path */
    private final Dispatcher dispatcher;

    private final Backend dispatchBackend;
    
    private final FS4ResourcePool fs4ResourcePool;
    
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
     * @param cacheParams   the size, lifetime, and controller of our cache
     * @param documentdbInfoConfig document database parameters
     */
    public FastSearcher(Backend dispatchBackend, FS4ResourcePool fs4ResourcePool,
                        Dispatcher dispatcher, SummaryParameters docSumParams, ClusterParams clusterParams,
                        CacheParams cacheParams, DocumentdbInfoConfig documentdbInfoConfig) {
        init(docSumParams, clusterParams, cacheParams, documentdbInfoConfig);
        this.dispatchBackend = dispatchBackend;
        this.fs4ResourcePool = fs4ResourcePool;
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

    protected void transformQuery(Query query) {
        QueryRewrite.rewriteSddocname(query);
    }

    @Override
    public Result doSearch2(Query query, QueryPacket queryPacket, CacheKey cacheKey, Execution execution) {
        if (dispatcher.searchCluster().groupSize() == 1)
            forceSinglePassGrouping(query);
        try(CloseableChannel channel = getChannel(query)) {
            Result result = channel.search(query, queryPacket, cacheKey);

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
     * Returns a request interface object for the given query.
     * Normally this is built from the backend field of this instance, which connects to the dispatch node
     * this component talks to (which is why this instance was chosen by the cluster controller). However,
     * under certain conditions we will instead return an interface which connects directly to the relevant
     * search nodes.
     */
    private CloseableChannel getChannel(Query query) {
        if (query.properties().getBoolean(dispatchInternal, false)) {
            Optional<CloseableChannel> dispatchedChannel = dispatcher.getDispatchedChannel(this, query);
            if (dispatchedChannel.isPresent()) {
                return dispatchedChannel.get();
            }
        }
        if (!query.properties().getBoolean(dispatchDirect, true))
            return new FS4CloseableChannel(this, query, dispatchBackend);
        if (query.properties().getBoolean(com.yahoo.search.query.Model.ESTIMATE))
            return new FS4CloseableChannel(this, query, dispatchBackend);

        Optional<SearchCluster.Node> directDispatchRecipient = dispatcher.searchCluster().directDispatchTarget();
        if (!directDispatchRecipient.isPresent())
            return new FS4CloseableChannel(this, query, dispatchBackend);

        // Dispatch directly to the single, local search node
        SearchCluster.Node local = directDispatchRecipient.get();
        query.trace(false, 2, "Dispatching directly to ", directDispatchRecipient.get());
        return new FS4CloseableChannel(this, query, fs4ResourcePool, local.hostname(), local.fs4port(), local.key());
    }

    /**
     * Perform a partial docsum fill for a temporary result
     * representing a partition of the complete fill request.
     *
     * @param result result containing a partition of the unfilled hits
     * @param summaryClass the summary class we want to fill with
     **/
    protected void doPartialFill(Result result, String summaryClass) {
        if (result.isFilled(summaryClass)) return;

        Query query = result.getQuery();
        traceQuery(getName(), "fill", query, query.getOffset(), query.getHits(), 1, quotedSummaryClass(summaryClass));

        if (query.properties().getBoolean(dispatchSummaries, true)
            && ! summaryNeedsQuery(query)
            && query.getRanking().getLocation() == null
            && ! cacheControl.useCache(query)
            && ! legacyEmulationConfigIsSet(getDocumentDatabase(query))) {

            CompressionType compression =
                CompressionType.valueOf(query.properties().getString(dispatchCompression, "LZ4").toUpperCase());
            dispatcher.fill(result, summaryClass, getDocumentDatabase(query), compression);
            return;
        }

        try (CloseableChannel channel = getChannel(query)) {
            channel.partialFill(result, summaryClass);
        }
    }

    private boolean legacyEmulationConfigIsSet(DocumentDatabase db) {
        LegacyEmulationConfig config = db.getDocsumDefinitionSet().legacyEmulationConfig();
        if (config.forceFillEmptyFields()) return true;
        if (config.stringBackedFeatureData()) return true;
        if (config.stringBackedStructuredData()) return true;
        return false;
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
